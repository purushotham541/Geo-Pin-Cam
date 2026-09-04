package com.letscode.geopincam.ui.photo

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.letscode.geopincam.GeoPinCamApp
import com.letscode.geopincam.R
import com.letscode.geopincam.data.preferences.SettingsRepository
import com.letscode.geopincam.data.storage.DeleteOutcome
import com.letscode.geopincam.data.storage.PhotoRepository
import com.letscode.geopincam.domain.model.AppSettings
import com.letscode.geopincam.domain.model.PhotoDetails
import com.letscode.geopincam.ui.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoDetailUiState(
    val isLoading: Boolean = true,
    val details: PhotoDetails? = null,
    val settings: AppSettings = AppSettings(),
    val notFound: Boolean = false,
    val isDeleted: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isRotating: Boolean = false,
    /** Bumped after a rotation so the image is decoded again instead of cached. */
    val imageRevision: Int = 0,
    val deleteConsentRequest: IntentSender? = null,
    val message: UserMessage? = null
)

/** Backs the full screen view of a single photograph. */
class PhotoDetailViewModel(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val photoId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoDetailUiState())
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    init {
        loadPhoto()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun loadPhoto() {
        viewModelScope.launch {
            photoRepository.loadPhoto(photoId).fold(
                onSuccess = { photo ->
                    if (photo == null) {
                        _uiState.update { it.copy(isLoading = false, notFound = true) }
                    } else {
                        val details = photoRepository.loadDetails(photo)
                        _uiState.update { it.copy(isLoading = false, details = details) }
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, notFound = true) }
                }
            )
        }
    }

    /** Turns the stored photo a quarter turn and reloads it from the media store. */
    fun onRotate(degrees: Int) {
        val photo = _uiState.value.details?.photo ?: return
        if (_uiState.value.isRotating) return

        _uiState.update { it.copy(isRotating = true) }
        viewModelScope.launch {
            val result = photoRepository.rotatePhoto(
                photo = photo,
                degrees = degrees,
                quality = _uiState.value.settings.camera.photoQuality
            )
            if (result.isSuccess) {
                // Swap width and height so the layout matches the new shape.
                val turned = photo.copy(width = photo.height, height = photo.width)
                _uiState.update { state ->
                    state.copy(
                        isRotating = false,
                        details = state.details?.copy(photo = turned),
                        imageRevision = state.imageRevision + 1,
                        message = UserMessage(R.string.photo_rotated)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRotating = false,
                        message = UserMessage(R.string.photo_rotate_failed)
                    )
                }
            }
        }
    }

    fun onDeleteRequested() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteConfirmed() {
        val uri = _uiState.value.details?.photo?.uri ?: return
        _uiState.update { it.copy(showDeleteConfirmation = false) }
        viewModelScope.launch {
            when (val outcome = photoRepository.delete(uri)) {
                DeleteOutcome.Deleted -> _uiState.update { it.copy(isDeleted = true) }

                is DeleteOutcome.NeedsUserConsent -> _uiState.update {
                    it.copy(deleteConsentRequest = outcome.intentSender)
                }

                is DeleteOutcome.Failed -> _uiState.update {
                    it.copy(message = UserMessage(R.string.photo_delete_failed))
                }
            }
        }
    }

    fun onDeleteConsentResult(granted: Boolean) {
        _uiState.update {
            it.copy(deleteConsentRequest = null, isDeleted = granted)
        }
    }

    fun onExportCopy() {
        val photo = _uiState.value.details?.photo ?: return
        viewModelScope.launch {
            val result = photoRepository.exportCopy(photo)
            _uiState.update {
                it.copy(
                    message = UserMessage(
                        if (result.isSuccess) {
                            R.string.photo_export_saved
                        } else {
                            R.string.capture_save_failed
                        }
                    )
                )
            }
        }
    }

    fun onShareFailed() {
        _uiState.update { it.copy(message = UserMessage(R.string.photo_share_failed)) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        /** Route argument carrying the media store id of the photo to show. */
        const val PHOTO_ID_ARG = "photoId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GeoPinCamApp
                // The navigation argument arrives through the saved state handle.
                val photoId = createSavedStateHandle().get<Long>(PHOTO_ID_ARG) ?: 0L
                PhotoDetailViewModel(
                    photoRepository = app.container.photoRepository,
                    settingsRepository = app.container.settingsRepository,
                    photoId = photoId
                )
            }
        }
    }
}
