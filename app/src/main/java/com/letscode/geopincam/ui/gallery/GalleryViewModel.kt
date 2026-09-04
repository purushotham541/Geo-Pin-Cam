package com.letscode.geopincam.ui.gallery

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.letscode.geopincam.GeoPinCamApp
import com.letscode.geopincam.R
import com.letscode.geopincam.data.storage.DeleteOutcome
import com.letscode.geopincam.data.storage.PhotoRepository
import com.letscode.geopincam.domain.model.CapturedPhoto
import com.letscode.geopincam.ui.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val isLoading: Boolean = true,
    val photos: List<CapturedPhoto> = emptyList(),
    val loadFailed: Boolean = false,
    val selectedPhoto: CapturedPhoto? = null,
    val message: UserMessage? = null,

    /** Set when Android needs the user to confirm a delete. */
    val deleteConsentRequest: IntentSender? = null
) {
    val isEmpty: Boolean get() = !isLoading && photos.isEmpty() && !loadFailed
}

/** Lists the photos taken with this app and handles share and delete actions. */
class GalleryViewModel(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    /** Remembered so a delete can be retried after the user grants consent. */
    private var pendingDeleteUri: Uri? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Only show the spinner on a cold load; a resume refresh should not
            // flash over a list that is already on screen.
            _uiState.update { it.copy(isLoading = it.photos.isEmpty()) }
            photoRepository.loadMedia().fold(
                onSuccess = { photos ->
                    _uiState.update {
                        it.copy(isLoading = false, photos = photos, loadFailed = false)
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(isLoading = false, loadFailed = true)
                    }
                }
            )
        }
    }

    fun onPhotoLongPressed(photo: CapturedPhoto) {
        _uiState.update { it.copy(selectedPhoto = photo) }
    }

    fun onSelectionDismissed() {
        _uiState.update { it.copy(selectedPhoto = null) }
    }

    fun deletePhoto(uri: Uri) {
        pendingDeleteUri = uri
        viewModelScope.launch {
            when (val outcome = photoRepository.delete(uri)) {
                DeleteOutcome.Deleted -> onDeleteSucceeded(uri)

                is DeleteOutcome.NeedsUserConsent -> _uiState.update {
                    it.copy(deleteConsentRequest = outcome.intentSender, selectedPhoto = null)
                }

                is DeleteOutcome.Failed -> _uiState.update {
                    it.copy(
                        selectedPhoto = null,
                        message = UserMessage(R.string.photo_delete_failed)
                    )
                }
            }
        }
    }

    fun onDeleteConsentResult(granted: Boolean) {
        val uri = pendingDeleteUri
        _uiState.update { it.copy(deleteConsentRequest = null) }
        if (!granted || uri == null) {
            pendingDeleteUri = null
            return
        }
        // The system removed the item once consent was given; just refresh.
        onDeleteSucceeded(uri)
    }

    private fun onDeleteSucceeded(uri: Uri) {
        pendingDeleteUri = null
        _uiState.update { state ->
            state.copy(
                photos = state.photos.filterNot { it.uri == uri },
                selectedPhoto = null,
                message = UserMessage(R.string.photo_deleted)
            )
        }
    }

    fun onShareFailed() {
        _uiState.update { it.copy(message = UserMessage(R.string.photo_share_failed)) }
    }

    fun onVideoPlayFailed() {
        _uiState.update { it.copy(message = UserMessage(R.string.video_play_failed)) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GeoPinCamApp
                GalleryViewModel(app.container.photoRepository)
            }
        }
    }
}
