package com.example.projet_mescontact_v4.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.projet_mescontact_v4.MainActivity
import com.example.projet_mescontact_v4.R
import com.example.projet_mescontact_v4.data.Contact
import com.example.projet_mescontact_v4.data.ContactViewModel
import com.example.projet_mescontact_v4.data.DbResult
import com.example.projet_mescontact_v4.data.PreferencesManager
import com.example.projet_mescontact_v4.databinding.FragmentDetailContactBinding
import com.example.projet_mescontact_v4.util.CoordinateValidator
import com.example.projet_mescontact_v4.util.ImageUtils
import com.example.projet_mescontact_v4.util.IntentUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Création (id = 0), consultation, modification et suppression d'un contact.
 * Le contact est relu dans la base à chaque affichage ; seul son _id est reçu (Bundle).
 */
class DetailContactFragment : Fragment(R.layout.fragment_detail_contact) {

    private var _binding: FragmentDetailContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()

    private val contactId: Long by lazy { requireArguments().getLong(ARG_CONTACT_ID, Contact.NEW_ID) }
    private val isCreation get() = contactId == Contact.NEW_ID

    /** Version enregistrée en base (null tant qu'elle n'est pas chargée). */
    private var original: Contact? = null
    /** Le formulaire a déjà été rempli : ne pas écraser la saisie (rotation, retour de la carte). */
    private var formInitialized = false
    private var currentPhotoPath: String? = null
    private var cameraTempFile: File? = null
    private val newPhotos = ArrayList<String>()
    private var busy = false
    private var deleteDialog: AlertDialog? = null

    // ---------------- Résultats d'activités ----------------
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) toast(R.string.msg_photo_selection_cancelled) else applyNewPhoto(uri)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = cameraTempFile
        cameraTempFile = null
        if (ok && temp != null && temp.exists() && temp.length() > 0) applyNewPhoto(Uri.fromFile(temp))
        else toast(R.string.msg_photo_capture_cancelled)
        temp?.delete()
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast(R.string.msg_camera_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { s ->
            formInitialized = s.getBoolean(STATE_INITIALIZED)
            currentPhotoPath = s.getString(STATE_PHOTO)
            cameraTempFile = s.getString(STATE_TEMP)?.let { File(it) }
            s.getStringArrayList(STATE_NEW_PHOTOS)?.let { newPhotos.addAll(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailContactBinding.bind(view)

        binding.btnDelete.visibility = if (isCreation) View.GONE else View.VISIBLE
        applyDisplayPrefs()
        setupListeners()
        setFormEnabled(false)

        // Read : chargement depuis la base (hors thread principal)
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = if (isCreation) Contact(nom = "", prenom = "", telephone = "")
            else viewModel.loadContact(contactId)
            if (_binding == null) return@launch
            if (loaded == null) {
                toast(R.string.msg_invalid_contact)
                (activity as? MainActivity)?.goBack()
                return@launch
            }
            original = loaded
            if (!formInitialized) fillForm(loaded)
            // Sinon les champs ont été restaurés automatiquement (état des vues).
            showPhoto()
            showSavedPosition(loaded)
            setFormEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(if (isCreation) R.string.title_new else R.string.title_detail)
    }

    private fun fillForm(c: Contact) {
        binding.editNom.setText(c.nom)
        binding.editPrenom.setText(c.prenom)
        binding.editTelephone.setText(c.telephone)
        binding.editEmail.setText(c.email.orEmpty())
        binding.editLatitude.setText(CoordinateValidator.format(c.latitude))
        binding.editLongitude.setText(CoordinateValidator.format(c.longitude))
        binding.checkFavori.isChecked = c.favori
        currentPhotoPath = c.photoUri
        formInitialized = true
    }

    /** Taille et couleur choisies dans les Paramètres. */
    private fun applyDisplayPrefs() {
        val p = PreferencesManager(requireContext()).display()
        listOf(binding.editNom, binding.editPrenom, binding.editTelephone).forEach {
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.tailleSp)
        }
        binding.editNom.setTextColor(p.couleur)
        binding.editPrenom.setTextColor(p.couleur)
    }

    private fun setupListeners() {
        binding.editNom.doAfterTextChanged { binding.layoutNom.error = null }
        binding.editPrenom.doAfterTextChanged { binding.layoutPrenom.error = null }
        binding.editTelephone.doAfterTextChanged { binding.layoutTelephone.error = null }
        binding.editEmail.doAfterTextChanged { binding.layoutEmail.error = null }
        binding.editLatitude.doAfterTextChanged { binding.layoutLatitude.error = null }
        binding.editLongitude.doAfterTextChanged { binding.layoutLongitude.error = null }

        binding.btnCall.setOnClickListener {
            val phone = binding.editTelephone.text.toString()
            if (phone.isBlank()) binding.layoutTelephone.error = getString(R.string.error_phone_empty)
            else IntentUtils.dial(requireContext(), phone)
        }
        binding.btnEmail.setOnClickListener { sendEmail() }
        binding.btnGallery.setOnClickListener { pickFromGallery() }
        binding.btnCamera.setOnClickListener { takePhoto() }
        binding.btnMap.setOnClickListener { openMap() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { cancel() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        // Retour (système ou flèche de la barre) = Annuler
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = cancel()
            })
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.progress.visibility = if (enabled) View.GONE else View.VISIBLE
        listOf(binding.btnSave, binding.btnDelete, binding.btnCall, binding.btnEmail,
            binding.btnGallery, binding.btnCamera, binding.btnMap).forEach { it.isEnabled = enabled }
    }

    private fun showSavedPosition(c: Contact) {
        binding.txtPosition.text = if (c.aUnePosition) getString(
            R.string.position_saved,
            CoordinateValidator.format(c.latitude), CoordinateValidator.format(c.longitude)
        ) else getString(R.string.position_none)
    }

    // ================= E-mail =================

    private fun sendEmail() {
        val email = binding.editEmail.text.toString().trim()
        when {
            email.isEmpty() -> { binding.layoutEmail.error = getString(R.string.error_email_empty); return }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.layoutEmail.error = getString(R.string.error_email_invalid); return
            }
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        }
        try { startActivity(intent) } catch (e: ActivityNotFoundException) { toast(R.string.msg_no_mail_app) }
    }

    // ================= Photo (copie dans filesDir) =================

    private fun pickFromGallery() {
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.msg_no_gallery)
        }
    }

    private fun takePhoto() {
        val ctx = requireContext()
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toast(R.string.msg_camera_unavailable); return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            launchCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val ctx = requireContext()
        val temp = ImageUtils.newPhotoFile(ctx, "tmp")
        cameraTempFile = temp
        try {
            takePicture.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", temp))
        } catch (e: ActivityNotFoundException) {
            cameraTempFile = null; temp.delete(); toast(R.string.msg_no_camera_app)
        } catch (e: SecurityException) {
            cameraTempFile = null; temp.delete(); toast(R.string.msg_camera_permission_denied)
        } catch (e: IllegalArgumentException) {
            cameraTempFile = null; temp.delete(); toast(R.string.msg_photo_load_failed)
        }
    }

    /** La galerie donne une URI temporaire : on en fait une copie JPEG durable dans filesDir. */
    private fun applyNewPhoto(source: Uri) {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val dest = ImageUtils.newPhotoFile(ctx, "photo")
            val ok = withContext(Dispatchers.IO) {
                ImageUtils.importPhoto(ctx, source, dest)
            }
            if (!ok) { toast(R.string.msg_photo_load_failed); return@launch }
            currentPhotoPath?.let { prev -> if (newPhotos.remove(prev)) ImageUtils.deleteQuietly(prev) }
            newPhotos.add(dest.path)
            currentPhotoPath = dest.path
            if (_binding != null) showPhoto()
        }
    }

    private fun showPhoto() {
        val bmp = ImageUtils.decodeScaled(currentPhotoPath, 512)
        if (bmp != null) binding.imgPhoto.setImageBitmap(bmp)
        else binding.imgPhoto.setImageResource(R.drawable.placeholder_avatar)
    }

    // ================= Carte =================

    private fun openMap() {
        val lat = binding.editLatitude.text.toString()
        val lng = binding.editLongitude.text.toString()
        showCoordinateErrors(CoordinateValidator.parse(lat, lng))
        val name = "${binding.editPrenom.text.toString().trim()} ${binding.editNom.text.toString().trim()}"
            .trim().ifEmpty { getString(R.string.title_new) }
        (requireActivity() as MainActivity).openMap(contactId, name, lat, lng)
    }

    private fun showCoordinateErrors(result: CoordinateValidator.Result): Boolean {
        if (result !is CoordinateValidator.Result.Invalid) return true
        result.latError?.let { binding.layoutLatitude.error = coordinateMessage(it, true) }
        result.lngError?.let { binding.layoutLongitude.error = coordinateMessage(it, false) }
        return false
    }

    private fun coordinateMessage(error: CoordinateValidator.Error, isLatitude: Boolean): String = when (error) {
        CoordinateValidator.Error.MISSING -> getString(R.string.error_coord_missing)
        CoordinateValidator.Error.NOT_A_NUMBER -> getString(R.string.error_coord_nan)
        CoordinateValidator.Error.OUT_OF_RANGE ->
            getString(if (isLatitude) R.string.error_lat_range else R.string.error_lng_range)
    }

    // ================= Create / Update =================

    private fun save() {
        val base = original ?: return
        if (busy) return
        val nom = binding.editNom.text.toString().trim()
        val prenom = binding.editPrenom.text.toString().trim()
        val phone = binding.editTelephone.text.toString().trim()
        val email = binding.editEmail.text.toString().trim()
        val coords = CoordinateValidator.parse(binding.editLatitude.text.toString(), binding.editLongitude.text.toString())

        // Validation AVANT tout accès à la base : rien d'invalide n'est écrit
        var firstInvalid: View? = null
        if (nom.isEmpty()) { binding.layoutNom.error = getString(R.string.error_last_name); firstInvalid = binding.editNom }
        if (prenom.isEmpty()) {
            binding.layoutPrenom.error = getString(R.string.error_first_name)
            firstInvalid = firstInvalid ?: binding.editPrenom
        }
        if (phone.isEmpty()) {
            binding.layoutTelephone.error = getString(R.string.error_phone_empty)
            firstInvalid = firstInvalid ?: binding.editTelephone
        } else if (!Patterns.PHONE.matcher(phone).matches()) {
            binding.layoutTelephone.error = getString(R.string.error_phone_invalid)
            firstInvalid = firstInvalid ?: binding.editTelephone
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.error_email_invalid)
            firstInvalid = firstInvalid ?: binding.editEmail
        }
        if (!showCoordinateErrors(coords)) {
            firstInvalid = firstInvalid ?: if (binding.layoutLatitude.error != null) binding.editLatitude else binding.editLongitude
        }
        if (firstInvalid != null) {
            firstInvalid.requestFocus()
            toast(R.string.msg_fix_errors)
            return
        }

        val (lat, lng) = when (coords) {
            is CoordinateValidator.Result.Valid -> coords.latitude to coords.longitude
            else -> null to null
        }
        val updated = base.copy(
            nom = nom, prenom = prenom, telephone = phone, email = email.ifEmpty { null },
            photoUri = currentPhotoPath, latitude = lat, longitude = lng,
            favori = binding.checkFavori.isChecked
        )

        busy = true
        setFormEnabled(false)
        viewModel.save(updated, oldPhoto = base.photoUri) { result ->
            busy = false
            if (_binding != null) setFormEnabled(true)
            when (result) {
                is DbResult.Inserted, DbResult.Updated -> {
                    newPhotos.filter { it != currentPhotoPath }.forEach { ImageUtils.deleteQuietly(it) }
                    newPhotos.clear()
                    toast(if (result is DbResult.Inserted) R.string.msg_inserted else R.string.msg_saved)
                    (activity as? MainActivity)?.goBack()
                }
                DbResult.NotFound -> toast(R.string.msg_invalid_contact)
                is DbResult.Failed -> toast(if (isCreation) R.string.msg_insert_failed else R.string.msg_update_failed)
                DbResult.Deleted -> Unit
            }
        }
    }

    // ================= Delete =================

    private fun confirmDelete() {
        val c = original ?: return
        deleteDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, c.nomComplet))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete) { _, _ -> delete(c) }
            .show()
    }

    private fun delete(c: Contact) {
        busy = true
        setFormEnabled(false)
        viewModel.delete(c) { result ->
            busy = false
            if (_binding != null) setFormEnabled(true)
            when (result) {
                DbResult.Deleted -> {
                    newPhotos.forEach { ImageUtils.deleteQuietly(it) }
                    newPhotos.clear()
                    toast(R.string.msg_deleted)
                    (activity as? MainActivity)?.goBack()
                }
                DbResult.NotFound -> toast(R.string.msg_invalid_contact)
                else -> toast(R.string.msg_delete_failed)
            }
        }
    }

    /** Annuler : rien n'est écrit en base, les anciennes valeurs sont conservées. */
    private fun cancel() {
        if (busy) return
        newPhotos.forEach { ImageUtils.deleteQuietly(it) }
        newPhotos.clear()
        cameraTempFile?.delete()
        cameraTempFile = null
        (requireActivity() as MainActivity).goBack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INITIALIZED, formInitialized)
        outState.putString(STATE_PHOTO, currentPhotoPath)
        outState.putString(STATE_TEMP, cameraTempFile?.path)
        outState.putStringArrayList(STATE_NEW_PHOTOS, newPhotos)
    }

    override fun onDestroyView() {
        deleteDialog?.dismiss()
        deleteDialog = null
        _binding = null
        super.onDestroyView()
    }

    private fun toast(resId: Int) {
        context?.let { Toast.makeText(it.applicationContext, resId, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val TAG = "detail"
        private const val ARG_CONTACT_ID = "contact_id"
        private const val STATE_INITIALIZED = "state_initialized"
        private const val STATE_PHOTO = "state_photo"
        private const val STATE_TEMP = "state_temp"
        private const val STATE_NEW_PHOTOS = "state_new_photos"

        fun newInstance(contactId: Long) = DetailContactFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}
