package app.logdate.feature.editor.ui.image

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.permissions.PermissionRequestContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android implementation of the image picker content.
 * Provides options to select an image from the gallery or take a photo.
 */
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showPermissionDialog = remember { mutableStateOf(false) }
    val requestedPermission = remember { mutableStateOf("") }
    
    // Remember the photo URI for camera captures
    val photoUri = remember { mutableStateOf<Uri?>(null) }
    
    // Function to create a URI for the camera image
    fun createImageUri(): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore on Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LogDate")
                }
                
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            } else {
                // For older Android versions, use existing URI constructor
                val imageCollection = MediaStore.Images.Media.getContentUri("external")
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                
                context.contentResolver.insert(imageCollection, contentValues)
            }
        } catch (e: Exception) {
            Napier.e("Error creating image URI", e)
            null
        }
    }
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            Napier.d("Gallery image selected: $it")
            onImageSelected(it.toString())
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri.value != null) {
            Napier.d("Camera photo captured: ${photoUri.value}")
            photoUri.value?.toString()?.let { onImageSelected(it) }
        }
    }
    
    // Permission request handler
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with appropriate action
            when (requestedPermission.value) {
                Manifest.permission.READ_EXTERNAL_STORAGE, 
                "android.permission.READ_MEDIA_IMAGES" -> {
                    // Launch gallery picker
                    galleryLauncher.launch("image/*")
                }
                Manifest.permission.CAMERA -> {
                    // Create URI for camera and launch
                    coroutineScope.launch {
                        val newUri = createImageUri()
                        if (newUri != null) {
                            photoUri.value = newUri
                            cameraLauncher.launch(newUri)
                        } else {
                            Napier.e("Failed to create photo URI")
                        }
                    }
                }
            }
        } else {
            // Show permission request explanation
            showPermissionDialog.value = true
        }
    }
    
    // Permission check functions
    fun checkStoragePermission() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        requestedPermission.value = storagePermission
        permissionLauncher.launch(storagePermission)
    }
    
    fun checkCameraPermission() {
        requestedPermission.value = Manifest.permission.CAMERA
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add an image to your entry",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Gallery button
                    OutlinedButton(
                        onClick = { checkStoragePermission() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                contentDescription = "Choose from gallery",
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text("Gallery")
                        }
                    }
                    
                    // Camera button 
                    OutlinedButton(
                        onClick = { checkCameraPermission() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "Take a photo",
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text("Camera")
                        }
                    }
                }
            }
        }
    }
    
    // Show permission request explanation if needed
    if (showPermissionDialog.value) {
        PermissionRequestContent(
            permission = requestedPermission.value,
            onRequestPermission = {
                permissionLauncher.launch(requestedPermission.value)
                showPermissionDialog.value = false
            },
            onDismiss = {
                showPermissionDialog.value = false
            }
        )
    }
}