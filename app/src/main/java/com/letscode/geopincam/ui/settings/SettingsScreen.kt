package com.letscode.geopincam.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letscode.geopincam.BuildConfig
import com.letscode.geopincam.R
import com.letscode.geopincam.domain.model.AddressDetail
import com.letscode.geopincam.domain.model.AppSettings
import com.letscode.geopincam.domain.model.CameraFacing
import com.letscode.geopincam.domain.model.CoordinateFormat
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.FlashMode
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.domain.model.StampConfiguration
import com.letscode.geopincam.domain.model.StampFontSize
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.model.TimeFormatStyle
import com.letscode.geopincam.domain.model.VideoQuality
import kotlin.math.roundToInt

/** Every user preference, grouped the way the product specification describes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            CameraSection(settings, viewModel)
            StampSection(settings, viewModel)
            StampFieldsSection(settings, viewModel)
            LocationSection(settings, viewModel)
            DateTimeSection(settings, viewModel)
            StorageSection(settings, viewModel)
            AboutSection(onOpenPrivacy, onOpenLicenses)
        }
    }
}

@Composable
private fun CameraSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsSection(stringResource(R.string.settings_section_camera)) {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_photo_quality),
            options = listOf(
                SettingsOption(PhotoQuality.HIGH, stringResource(R.string.settings_quality_high)),
                SettingsOption(
                    PhotoQuality.MEDIUM,
                    stringResource(R.string.settings_quality_medium)
                ),
                SettingsOption(PhotoQuality.LOW, stringResource(R.string.settings_quality_low))
            ),
            selected = settings.camera.photoQuality,
            onSelect = viewModel::setPhotoQuality
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_video_quality),
            options = listOf(
                SettingsOption(VideoQuality.UHD, stringResource(R.string.settings_video_uhd)),
                SettingsOption(
                    VideoQuality.FULL_HD,
                    stringResource(R.string.settings_video_full_hd)
                ),
                SettingsOption(VideoQuality.HD, stringResource(R.string.settings_video_hd))
            ),
            selected = settings.camera.videoQuality,
            onSelect = viewModel::setVideoQuality
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_flash_preference),
            options = listOf(
                SettingsOption(FlashMode.OFF, stringResource(R.string.camera_flash_off)),
                SettingsOption(FlashMode.AUTO, stringResource(R.string.camera_flash_auto)),
                SettingsOption(FlashMode.ON, stringResource(R.string.camera_flash_on)),
                SettingsOption(FlashMode.TORCH, stringResource(R.string.camera_flash_torch))
            ),
            selected = settings.camera.flashMode,
            onSelect = viewModel::setFlashMode
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_default_camera),
            options = listOf(
                SettingsOption(CameraFacing.BACK, stringResource(R.string.settings_camera_back)),
                SettingsOption(CameraFacing.FRONT, stringResource(R.string.settings_camera_front))
            ),
            selected = settings.camera.defaultFacing,
            onSelect = viewModel::setDefaultFacing
        )
    }
}

@Composable
private fun StampSection(settings: AppSettings, viewModel: SettingsViewModel) {
    val stamp = settings.stamp
    SettingsSection(stringResource(R.string.settings_section_stamp)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_stamp_enabled),
            subtitle = stringResource(R.string.settings_stamp_enabled_summary),
            checked = stamp.stampEnabled,
            onCheckedChange = viewModel::setStampEnabled
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_stamp_position),
            options = listOf(
                SettingsOption(
                    StampPosition.TOP_LEFT,
                    stringResource(R.string.settings_position_top_left)
                ),
                SettingsOption(
                    StampPosition.TOP_CENTER,
                    stringResource(R.string.settings_position_top_center)
                ),
                SettingsOption(
                    StampPosition.TOP_RIGHT,
                    stringResource(R.string.settings_position_top_right)
                ),
                SettingsOption(
                    StampPosition.BOTTOM_LEFT,
                    stringResource(R.string.settings_position_bottom_left)
                ),
                SettingsOption(
                    StampPosition.BOTTOM_CENTER,
                    stringResource(R.string.settings_position_bottom_center)
                ),
                SettingsOption(
                    StampPosition.BOTTOM_RIGHT,
                    stringResource(R.string.settings_position_bottom_right)
                )
            ),
            selected = stamp.position,
            onSelect = viewModel::setStampPosition,
            enabled = stamp.stampEnabled
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_font_size),
            options = listOf(
                SettingsOption(StampFontSize.SMALL, stringResource(R.string.settings_font_small)),
                SettingsOption(StampFontSize.MEDIUM, stringResource(R.string.settings_font_medium)),
                SettingsOption(StampFontSize.LARGE, stringResource(R.string.settings_font_large)),
                SettingsOption(
                    StampFontSize.EXTRA_LARGE,
                    stringResource(R.string.settings_font_extra_large)
                )
            ),
            selected = stamp.fontSize,
            onSelect = viewModel::setFontSize,
            enabled = stamp.stampEnabled
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_text_alignment),
            options = listOf(
                SettingsOption(
                    StampTextAlignment.START,
                    stringResource(R.string.settings_alignment_start)
                ),
                SettingsOption(
                    StampTextAlignment.CENTER,
                    stringResource(R.string.settings_alignment_center)
                ),
                SettingsOption(
                    StampTextAlignment.END,
                    stringResource(R.string.settings_alignment_end)
                )
            ),
            selected = stamp.textAlignment,
            onSelect = viewModel::setTextAlignment,
            enabled = stamp.stampEnabled
        )
        SettingsSliderRow(
            title = stringResource(R.string.settings_background_opacity),
            valueLabel = stringResource(
                R.string.settings_background_opacity_value,
                (stamp.backgroundOpacity * PERCENT).roundToInt()
            ),
            value = stamp.backgroundOpacity,
            valueRange = StampConfiguration.MIN_BACKGROUND_OPACITY..
                StampConfiguration.MAX_BACKGROUND_OPACITY,
            onValueChange = viewModel::setBackgroundOpacity,
            enabled = stamp.stampEnabled
        )
    }
}

@Composable
private fun StampFieldsSection(settings: AppSettings, viewModel: SettingsViewModel) {
    val stamp = settings.stamp
    SettingsSection(stringResource(R.string.settings_section_stamp_fields)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_address),
            subtitle = stringResource(R.string.settings_field_address_summary),
            checked = stamp.showAddress,
            onCheckedChange = viewModel::setShowAddress,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_place_name),
            subtitle = stringResource(R.string.settings_field_place_name_summary),
            checked = stamp.showPlaceName,
            onCheckedChange = viewModel::setShowPlaceName,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_country_flag),
            checked = stamp.showCountryFlag,
            onCheckedChange = viewModel::setShowCountryFlag,
            enabled = stamp.stampEnabled && stamp.showAddress
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_map),
            subtitle = stringResource(R.string.settings_field_map_summary),
            checked = stamp.showMapThumbnail,
            onCheckedChange = viewModel::setShowMapThumbnail,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_coordinates),
            checked = stamp.showCoordinates,
            onCheckedChange = viewModel::setShowCoordinates,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_date),
            checked = stamp.showDate,
            onCheckedChange = viewModel::setShowDate,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_time),
            checked = stamp.showTime,
            onCheckedChange = viewModel::setShowTime,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_accuracy),
            checked = stamp.showAccuracy,
            onCheckedChange = viewModel::setShowAccuracy,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_altitude),
            checked = stamp.showAltitude,
            onCheckedChange = viewModel::setShowAltitude,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_speed),
            checked = stamp.showSpeed,
            onCheckedChange = viewModel::setShowSpeed,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_bearing),
            checked = stamp.showBearing,
            onCheckedChange = viewModel::setShowBearing,
            enabled = stamp.stampEnabled
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_field_app_name),
            checked = stamp.showAppName,
            onCheckedChange = viewModel::setShowAppName,
            enabled = stamp.stampEnabled
        )
        if (stamp.stampEnabled && stamp.hasNoVisibleFields) {
            Text(
                text = stringResource(R.string.settings_fields_all_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = WARNING_PADDING)
            )
        }
    }
}

@Composable
private fun LocationSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsSection(stringResource(R.string.settings_section_location)) {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_coordinate_format),
            options = listOf(
                SettingsOption(
                    CoordinateFormat.LABELLED,
                    stringResource(R.string.settings_coordinate_labelled)
                ),
                SettingsOption(
                    CoordinateFormat.DECIMAL,
                    stringResource(R.string.settings_coordinate_decimal)
                ),
                SettingsOption(
                    CoordinateFormat.DMS,
                    stringResource(R.string.settings_coordinate_dms)
                )
            ),
            selected = settings.stamp.coordinateFormat,
            onSelect = viewModel::setCoordinateFormat
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_address_detail),
            options = listOf(
                SettingsOption(
                    AddressDetail.SHORT,
                    stringResource(R.string.settings_address_short)
                ),
                SettingsOption(AddressDetail.FULL, stringResource(R.string.settings_address_full))
            ),
            selected = settings.addressDetail,
            onSelect = viewModel::setAddressDetail
        )
    }
}

@Composable
private fun DateTimeSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsSection(stringResource(R.string.settings_section_datetime)) {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_date_format),
            options = DateFormatStyle.entries.map { SettingsOption(it, it.pattern) },
            selected = settings.stamp.dateFormat,
            onSelect = viewModel::setDateFormat
        )
        SettingsChoiceRow(
            title = stringResource(R.string.settings_time_format),
            options = listOf(
                SettingsOption(
                    TimeFormatStyle.HOUR_12_WITH_ZONE,
                    stringResource(R.string.settings_time_12_hour_zone)
                ),
                SettingsOption(
                    TimeFormatStyle.HOUR_12,
                    stringResource(R.string.settings_time_12_hour)
                ),
                SettingsOption(
                    TimeFormatStyle.HOUR_12_WITH_SECONDS,
                    stringResource(R.string.settings_time_12_hour_seconds)
                ),
                SettingsOption(
                    TimeFormatStyle.HOUR_24,
                    stringResource(R.string.settings_time_24_hour)
                ),
                SettingsOption(
                    TimeFormatStyle.HOUR_24_WITH_SECONDS,
                    stringResource(R.string.settings_time_24_hour_seconds)
                ),
                SettingsOption(
                    TimeFormatStyle.HOUR_24_WITH_ZONE,
                    stringResource(R.string.settings_time_24_hour_zone)
                )
            ),
            selected = settings.stamp.timeFormat,
            onSelect = viewModel::setTimeFormat
        )
    }
}

@Composable
private fun StorageSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsSection(stringResource(R.string.settings_section_storage)) {
        SettingsInfoRow(
            title = stringResource(R.string.settings_save_location),
            value = stringResource(R.string.settings_save_location_value)
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_save_stamped),
            checked = settings.storage.saveStampedPhoto,
            onCheckedChange = viewModel::setSaveStampedPhoto
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_save_original),
            subtitle = stringResource(R.string.settings_save_original_summary),
            checked = settings.storage.saveOriginalPhoto,
            onCheckedChange = viewModel::setSaveOriginalPhoto
        )
    }
}

@Composable
private fun AboutSection(onOpenPrivacy: () -> Unit, onOpenLicenses: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_about)) {
        SettingsInfoRow(
            title = stringResource(R.string.about_app_name),
            value = stringResource(R.string.app_name)
        )
        SettingsInfoRow(
            title = stringResource(R.string.about_version),
            value = stringResource(
                R.string.about_version_value,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
        )
        SettingsNavigationRow(
            title = stringResource(R.string.about_privacy),
            onClick = onOpenPrivacy
        )
        SettingsNavigationRow(
            title = stringResource(R.string.about_licenses),
            onClick = onOpenLicenses
        )
    }
}

private const val PERCENT = 100
private val WARNING_PADDING = 16.dp
