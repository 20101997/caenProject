package com.grupposts.trasporti.features.departments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.grupposts.domain.models.ActionType
import com.grupposts.trasporti.features.MainViewModel
import com.grupposts.trasporti.R
import com.grupposts.trasporti.databinding.FragmentDepartmentActionBinding
import com.grupposts.trasporti.features.rfid.models.BleConnectionActivity
import com.grupposts.trasporti.utils.showError
import com.grupposts.trasporti.utils.showToolbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DepartmentActionFragment : Fragment(R.layout.fragment_department_action) {

    private lateinit var binding: FragmentDepartmentActionBinding

    private val viewModel: DepartmentViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        showToolbar(
            mainViewModel.selectedJourney?.structure?.name,
            mainViewModel.selectedDepartment?.name
        )
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDepartmentActionBinding.bind(view)

        //Here you go to bleConnection
        binding.cvDelivery.setOnClickListener {
            mainViewModel.selectedActionType = ActionType.DELIVERY
            val mReader = BleConnectionActivity.Readers ;
            Log.d("hey there", mReader.toString())
            if(mReader.isNullOrEmpty()) {
                val intent = Intent(requireContext(), BleConnectionActivity::class.java)
                startActivity(intent)
            }
           // findNavController().navigate(DepartmentActionFragmentDirections.actionDepartmentActionFragmentToContainerNavigation())


        }

        binding.cvCollect.setOnClickListener {
            mainViewModel.selectedActionType = ActionType.WITHDRAWAL
            findNavController().navigate(DepartmentActionFragmentDirections.actionDepartmentActionFragmentToContainerNavigation())
        }

        setObservers()
    }

    private fun setObservers() {
        with(viewModel) {
            loading.observe(viewLifecycleOwner) {

            }

            error.observe(viewLifecycleOwner) {
                showError(it)
            }
        }
    }
}