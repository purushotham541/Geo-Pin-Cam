package com.letscode.geopincam.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.letscode.geopincam.ui.camera.CameraScreen
import com.letscode.geopincam.ui.gallery.GalleryScreen
import com.letscode.geopincam.ui.photo.PhotoDetailScreen
import com.letscode.geopincam.ui.photo.PhotoDetailViewModel
import com.letscode.geopincam.ui.settings.LicensesScreen
import com.letscode.geopincam.ui.settings.PrivacyScreen
import com.letscode.geopincam.ui.settings.SettingsScreen
import com.letscode.geopincam.ui.splash.BrandSplashScreen

/** Every destination in the app. */
object Destinations {
    const val SPLASH = "splash"
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val PRIVACY = "settings/privacy"
    const val LICENSES = "settings/licenses"

    const val PHOTO = "photo/{${PhotoDetailViewModel.PHOTO_ID_ARG}}"

    fun photoRoute(photoId: Long) = "photo/$photoId"
}

/**
 * The branded splash opens the app and then removes itself from the back stack,
 * so the camera behaves as the start destination: everything else is pushed on
 * top of it and the hardware back button always leads back to the viewfinder.
 */
@Composable
fun GeoPinCamNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        modifier = modifier
    ) {
        composable(Destinations.SPLASH) {
            BrandSplashScreen(
                onDone = {
                    navController.navigate(Destinations.CAMERA) {
                        popUpTo(Destinations.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Destinations.CAMERA) {
            // Captures are saved to the gallery on the spot; the viewfinder stays
            // on screen so the next shot is never held up by a review step.
            CameraScreen(
                onOpenGallery = { navController.navigate(Destinations.GALLERY) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) }
            )
        }

        composable(Destinations.GALLERY) {
            GalleryScreen(
                onBack = navController::popBackStack,
                onOpenPhoto = { id -> navController.navigate(Destinations.photoRoute(id)) }
            )
        }

        composable(
            route = Destinations.PHOTO,
            arguments = listOf(
                navArgument(PhotoDetailViewModel.PHOTO_ID_ARG) { type = NavType.LongType }
            )
        ) {
            PhotoDetailScreen(onBack = navController::popBackStack)
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onOpenPrivacy = { navController.navigate(Destinations.PRIVACY) },
                onOpenLicenses = { navController.navigate(Destinations.LICENSES) }
            )
        }

        composable(Destinations.PRIVACY) {
            PrivacyScreen(onBack = navController::popBackStack)
        }

        composable(Destinations.LICENSES) {
            LicensesScreen(onBack = navController::popBackStack)
        }
    }
}
