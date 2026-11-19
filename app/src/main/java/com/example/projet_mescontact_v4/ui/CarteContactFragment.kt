package com.example.projet_mescontact_v4.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.projet_mescontact_v4.BuildConfig
import com.example.projet_mescontact_v4.R
import com.example.projet_mescontact_v4.databinding.FragmentCarteContactBinding
import com.example.projet_mescontact_v4.util.CoordinateValidator
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Écran 3 : position du contact sur Google Maps (carte centrée + marqueur au nom du contact).
 * Aucune permission de localisation : on n'affiche que des coordonnées déjà saisies.
 */
class CarteContactFragment : Fragment(R.layout.fragment_carte_contact) {

    private var _binding: FragmentCarteContactBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCarteContactBinding.bind(view)

        val args = requireArguments()
        val name = args.getString(ARG_NAME).orEmpty()
        // Retour (système ou flèche de la barre) : la pile du FragmentManager revient au détail.

        val result = CoordinateValidator.parse(args.getString(ARG_LAT), args.getString(ARG_LNG))
        when {
            result is CoordinateValidator.Result.Empty -> showMessage(getString(R.string.map_no_coordinates, name))
            result is CoordinateValidator.Result.Invalid -> showMessage(getString(R.string.map_invalid_coordinates))
            !BuildConfig.HAS_MAPS_KEY -> showMessage(getString(R.string.map_no_api_key))
            !playServicesAvailable() -> showMessage(getString(R.string.map_no_play_services))
            result is CoordinateValidator.Result.Valid -> showMap(name, result.latitude, result.longitude)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.title_map, requireArguments().getString(ARG_NAME).orEmpty())
    }

    private fun playServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext()) ==
            ConnectionResult.SUCCESS

    private fun showMessage(text: String) {
        binding.mapContainer.visibility = View.GONE
        binding.txtCoordinates.visibility = View.GONE
        binding.txtMapMessage.visibility = View.VISIBLE
        binding.txtMapMessage.text = text
    }

    private fun showMap(name: String, lat: Double, lng: Double) {
        binding.txtMapMessage.visibility = View.GONE
        binding.mapContainer.visibility = View.VISIBLE
        binding.txtCoordinates.visibility = View.VISIBLE
        binding.txtCoordinates.text = getString(
            R.string.position_saved, CoordinateValidator.format(lat), CoordinateValidator.format(lng)
        )

        // SupportMapFragment = fragment ENFANT ; on ne l'ajoute qu'une fois (pas de doublon à la rotation)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_container) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().replace(R.id.map_container, it).commitNow()
            }

        mapFragment.getMapAsync { map ->
            val position = LatLng(lat, lng)
            map.clear()
            map.uiSettings.isZoomControlsEnabled = true
            map.addMarker(
                MarkerOptions().position(position).title(name)
                    .snippet("${CoordinateValidator.format(lat)}, ${CoordinateValidator.format(lng)}")
            )?.showInfoWindow()
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, ZOOM))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "carte"
        private const val ZOOM = 14f
        private const val ARG_ID = "contact_id"
        private const val ARG_NAME = "contact_name"
        private const val ARG_LAT = "latitude"
        private const val ARG_LNG = "longitude"

        fun newInstance(contactId: Long, name: String, latitude: String, longitude: String) =
            CarteContactFragment().apply {
                arguments = bundleOf(
                    ARG_ID to contactId, ARG_NAME to name, ARG_LAT to latitude, ARG_LNG to longitude
                )
            }
    }
}
