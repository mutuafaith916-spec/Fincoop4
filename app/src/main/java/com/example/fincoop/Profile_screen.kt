package com.example.fincoop

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : SecureActivity() {

    private val repository by lazy { FincoopRepository(this) }
    private val userEmail by lazy { intent.getStringExtra("USER_EMAIL") ?: "user@example.com" }
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                repository.updateProfileImage(it.toString(), userEmail)
                findViewById<ImageView>(R.id.profileImage).setImageURI(it)
                Snackbar.make(findViewById(android.R.id.content), "Profile picture updated", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Handle cases where permission cannot be persisted
                repository.updateProfileImage(it.toString(), userEmail)
                findViewById<ImageView>(R.id.profileImage).setImageURI(it)
            }
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let {
            val file = File(filesDir, "profile_camera_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(file).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = Uri.fromFile(file)
                repository.updateProfileImage(uri.toString(), userEmail)
                findViewById<ImageView>(R.id.profileImage).setImageBitmap(it)
                Snackbar.make(findViewById(android.R.id.content), "Photo captured successfully", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Error saving photo", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_screen)

        setupUserInfo()
        setupClickListeners()
        setupThemeSwitch()
    }

    private fun setupUserInfo() {
        val name = repository.getFormattedUserName(userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }, userEmail)
        val phone = repository.getUserPhone(userEmail)
        val imageUri = repository.getUserImage(userEmail)
        val regNo = repository.getRegistrationNumber(userEmail)
        val accountNo = repository.getAccountNumber(userEmail)
        
        findViewById<TextView>(R.id.txtFullName).text = name
        findViewById<TextView>(R.id.txtEmail).text = "$userEmail\nReg: $regNo"
        findViewById<TextView>(R.id.txtPhone).text = "$phone\nAcc: $accountNo"
        
        val regLoc = repository.getRegLocation()
        if (regLoc != null) {
            findViewById<TextView>(R.id.txtEmail).append("\nReg Location: ${String.format("%.4f", regLoc.first)}, ${String.format("%.4f", regLoc.second)}")
        }
        
        val profileImageView = findViewById<ImageView>(R.id.profileImage)
        if (imageUri != null) {
            try {
                profileImageView.setImageURI(Uri.parse(imageUri))
            } catch (e: Exception) {
                profileImageView.setImageResource(R.mipmap.ic_launcher_round)
            }
        } else {
            profileImageView.setImageResource(R.mipmap.ic_launcher_round)
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnChangePhoto).setOnClickListener {
            showImageOptionsDialog()
        }

        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            showEditProfileDialog()
        }

        findViewById<View>(R.id.btnChangePassword).setOnClickListener {
            showChangePinDialog()
        }

        findViewById<View>(R.id.btnLogout).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.profileToolbar).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showImageOptionsDialog() {
        val options = arrayOf("Change Photo (Gallery)", "Take Photo (Camera)", "Remove Photo")
        AlertDialog.Builder(this)
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImage.launch("image/*")
                    1 -> {
                        if (PermissionHelper.hasCameraPermission(this)) {
                            openCamera()
                        } else {
                            PermissionHelper.requestCameraPermission(this, 1002)
                        }
                    }
                    2 -> {
                        repository.deleteProfileImage(userEmail)
                        findViewById<ImageView>(R.id.profileImage).setImageResource(R.mipmap.ic_launcher_round)
                        Snackbar.make(findViewById(android.R.id.content), "Profile picture removed", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCamera() {
        takePhoto.launch()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            if (PermissionHelper.hasCameraPermission(this)) {
                openCamera()
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Camera permission is required to take photos", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupThemeSwitch() {
        val themeSwitch = findViewById<MaterialSwitch>(R.id.profileThemeSwitch)
        themeSwitch.isChecked = repository.isDarkMode()
        
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            repository.setDarkMode(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun showEditProfileDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 0)
        }

        val tilName = TextInputLayout(context)
        tilName.hint = "Full Name"
        
        val etName = TextInputEditText(context)
        etName.setText(findViewById<TextView>(R.id.txtFullName).text)
        etName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        tilName.addView(etName)

        val tilPhone = TextInputLayout(context)
        tilPhone.hint = "Phone Number"
        val phoneContainerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        phoneContainerParams.topMargin = 32
        tilPhone.layoutParams = phoneContainerParams
        
        val etPhone = TextInputEditText(context)
        etPhone.setText(findViewById<TextView>(R.id.txtPhone).text)
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        tilPhone.addView(etPhone)

        layout.addView(tilName)
        layout.addView(tilPhone)

        AlertDialog.Builder(context)
            .setTitle("Edit Personal Details")
            .setView(layout)
            .setPositiveButton("Save Changes") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                
                if (newName.isNotEmpty() && newPhone.isNotEmpty()) {
                    repository.updateProfile(newName, newPhone, userEmail)
                    setupUserInfo()
                    Snackbar.make(findViewById(android.R.id.content), "Details updated successfully", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), "All fields are required", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangePinDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 40, 80, 0)
        }

        val oldPinInput = EditText(this)
        oldPinInput.hint = "Current PIN"
        oldPinInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        
        val newPinInput = EditText(this)
        newPinInput.hint = "New 4-digit PIN"
        newPinInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        
        container.addView(oldPinInput)
        container.addView(newPinInput)

        AlertDialog.Builder(this)
            .setTitle("Update Transaction PIN")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val oldPin = oldPinInput.text.toString()
                val newPin = newPinInput.text.toString()
                
                if (repository.verifyPin(oldPin, userEmail)) {
                    if (newPin.length == 4) {
                        repository.updatePin(newPin, userEmail)
                        Snackbar.make(findViewById(android.R.id.content), "PIN updated successfully", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), "New PIN must be 4 digits", Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    Snackbar.make(findViewById(android.R.id.content), "Incorrect current PIN", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
