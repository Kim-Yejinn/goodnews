package com.saveurlife.goodnews.family

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.saveurlife.goodnews.MapsFragment
import com.saveurlife.goodnews.R
import com.saveurlife.goodnews.databinding.FragmentFamilyPlaceAddEditBinding
import com.saveurlife.goodnews.family.FamilyFragment.Mode

class FamilyPlaceAddEditFragment : DialogFragment() {

    private lateinit var binding: FragmentFamilyPlaceAddEditBinding
    private lateinit var geocoder: Geocoder

    private lateinit var mapsFragment: MapsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val mode = it.getSerializable("mode") as Mode
            val seqNumber = it.getInt("seq")

//            updateUIForMode(mode, placeId)
        }
    }

//    private fun updateUIForMode(mode: Mode, dataId: Int) {
//        when (mode) {
//            Mode.ADD -> { /* 추가 모드 설정 */
//
//            }
//
//            Mode.READ -> {
//                // 데이터 로드 및 UI 업데이트
//                val data = loadData(dataId)
//                displayData(data)
//            }
//
//            Mode.EDIT -> { /* 수정 모드 설정 */
//            }
//        }
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFamilyPlaceAddEditBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 구글 서치 박스 ui 변경
        val autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.meetingPlaceAutocompleteFragment) as AutocompleteSupportFragment
        autocompleteFragment.view?.setBackgroundResource(R.drawable.input_stroke_none)
        autocompleteFragment.view?.findViewById<EditText>(com.google.android.libraries.places.R.id.places_autocomplete_search_input)
            ?.apply {
                hint = "주소를 검색해 주세요."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }

        // 등록 버튼 눌렀을 때
        binding.meetingPlaceAddSubmit.setOnClickListener {
            // 추가 @@

            dismiss() // 다이얼로그 닫기
        }

        binding.meetingPlaceAddCancel.setOnClickListener {
            dismiss()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapsFragment = MapsFragment()
        childFragmentManager.beginTransaction().apply {
            add(R.id.meetingPlaceMapView, mapsFragment)
            commit()
        }

        geocoder = Geocoder(requireActivity())

        // AutocompleteSupportFragment 설정
        val autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.meetingPlaceAutocompleteFragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG
            )
        )
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                // 사용자가 선택한 장소로 지도 이동
                place.latLng?.let {
                    mapsFragment.setLocation(it.latitude, it.longitude)
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Log.i("AutocompleteError", "An error occurred: $status")
            }
        })
    }

}