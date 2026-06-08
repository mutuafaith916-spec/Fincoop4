package com.example.fincoop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ProfileActivity : AppCompatActivity() {

    private val repository by lazy { FincoopRepository(this) }
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            repository.updateProfileImage(it.toString())
            findViewById<ImageView>(R.id.profileImage).setImageURI(it)
            Snackbar.make(findViewById(android.R.id.content), "Profile picture updated", Snackbar.LENGTH_SHORT).show()
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
        val email = intent.getStringExtra("USER_EMAIL") ?: "user@example.com"
        val name = repository.getUserName(email.substringBefore("@").replaceFirstChar { it.uppercase() })
        val phone = repository.getUserPhone()
        val imageUri = repository.getUserImage()
        
        findViewById<TextView>(R.id.txtFullName).text = name
        findViewById<TextView>(R.id.txtEmail).text = email
        findViewById<TextView>(R.id.txtPhone).text = phone
        
        imageUri?.let {
            try {
                findViewById<ImageView>(R.id.profileImage).setImageURI(Uri.parse(it))
            } catch (e: Exception) {
                // Fallback if URI is inaccessible
            }
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnChangePhoto).setOnClickListener {
            pickImage.launch("image/*")
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
                    repository.updateProfile(newName, newPhone)
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
        val oldPinInput = EditText(this)
        oldPinInput.hint = "Current PIN"
        oldPinInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        
        val newPinInput = EditText(this)
        newPinInput.hint = "New 4-digit PIN"
        newPinInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 40, 80, 0)
            addView(oldPinInput)
            addView(newPinInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Update Transaction PIN")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val oldPin = oldPinInput.text.toString()
                val newPin = newPinInput.text.toString()
                
                if (repository.verifyPin(oldPin)) {
                    if (newPin.length == 4) {
                        repository.updatePin(newPin)
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
