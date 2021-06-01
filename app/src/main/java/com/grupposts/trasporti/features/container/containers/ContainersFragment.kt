package com.grupposts.trasporti.features.container.containers

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.grupposts.domain.models.ActionType
import com.grupposts.domain.models.Container
import com.grupposts.trasporti.R
import com.grupposts.trasporti.databinding.FragmentContainersBinding
import com.grupposts.trasporti.features.MainViewModel
import com.grupposts.trasporti.features.container.*
import com.grupposts.trasporti.utils.showAlertDialog
import com.grupposts.trasporti.utils.showError
import com.grupposts.trasporti.utils.showToolbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ContainersFragment : Fragment(R.layout.fragment_containers) {

    private lateinit var binding: FragmentContainersBinding
    private lateinit var adapter: ContainerListAdapter

    private val viewModel: ContainerViewModel by navGraphViewModels(R.id.container_navigation) { defaultViewModelProviderFactory }
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            navigateBackToDepartments()
        }
    }

    override fun onResume() {
        super.onResume()
        showToolbar(
            mainViewModel.selectedJourney?.structure?.name,
            mainViewModel.selectedDepartment?.name
        ) {
            navigateBackToDepartments()
        }

        when (mainViewModel.selectedActionType) {
            ActionType.DELIVERY -> {
                viewModel.onDeliveryAction(
                    mainViewModel.selectedTravel?.id,
                    mainViewModel.selectedDepartment?.id
                )
            }

            ActionType.WITHDRAWAL -> {
                viewModel.onWithdrawalAction(mainViewModel.selectedTravel?.id)
            }

            else -> viewModel.getContainers(mainViewModel.selectedTravel?.id)
        }
    }

    private fun navigateBackToDepartments() {
        showAlertDialog(
            message = getString(R.string.containers_back_alert_message),
            onPositive = { viewModel.deleteContainers(mainViewModel.selectedTravel?.id) }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentContainersBinding.bind(view)

        setupUI()
        setObserver()
    }

    private fun setupUI() {
        with(binding) {
            progressIndicator.hide()

            cvNewContainer.setOnClickListener {
                viewModel.onNewContainerClicked()
            }

            btnSignWithdrawal.setOnClickListener {
                findNavController().navigate(ContainersFragmentDirections.actionContainersFragmentToSignatureFragment())
            }

            adapter = ContainerListAdapter({ container, name ->
                if(false/*mainViewModel.selectedActionType == ActionType.WITHDRAWAL*/)

                { setFragmentResult(CONTAINER_REQUEST_KEY, bundleOf(CONTAINER_PARAM_KEY to container))
                    val action =
                        ContainersFragmentDirections.actionContainersFragmentToAddContainerFragment()
                    action.name = name
                    findNavController().navigate(action)
                }
                else {

                    Log.d("wadhah", "hello world")
                    if(container.temperatureTracking==1)  showTemperatureContainerAlertDialog(container, name)
                    else Unit
                    true
                }
            }, { container, name ->

                Log.d("wadhah", "hello world")
                showDeleteContainerAlertDialog(container, name)
                true
            })

            rvContainerList.adapter = adapter
        }
    }

    private fun showDeleteContainerAlertDialog(container: Container, name: String) {
        showAlertDialog(
            message = getString(R.string.delete_container_alert_message, name),
            onPositive = {
                viewModel.deleteContainer(
                    mainViewModel.selectedTravel?.id,
                    container.id
                )
            }
        )
    }

    //here you update
    private fun showTemperatureContainerAlertDialog(container: Container, name: String) {
        Log.d("code", container.code.toString());
        showAlertDialog(
            title = "Tag: "+container.code.toString(),
            message = "do you want to update this container tempurature",

            onPositive = {


                viewModel.updateTemperatue(
                    mainViewModel.selectedTravel?.id,
                    container.id,container.code



                )
            },

            )






    }












    private fun setObserver() {
        with(viewModel) {
            loading.observe(viewLifecycleOwner) {
                if (it) {
                    binding.progressIndicator.show()
                } else {
                    binding.progressIndicator.hide()
                }
            }

            error.observe(viewLifecycleOwner) {
                showError(it)
            }

            containers.observe(viewLifecycleOwner) {
                adapter.submitList(it)
            }

            newContainerVisibility.observe(viewLifecycleOwner) {
                binding.cvNewContainer.visibility = it
            }

            signButtonText.observe(viewLifecycleOwner) {
                binding.btnSignWithdrawal.text = it
            }

            navigateToAddContainer.observe(viewLifecycleOwner) {
                findNavController().navigate(
                    ContainersFragmentDirections.actionContainersFragmentToAddContainerFragment()
                )
            }

            navigateToDepartments.observe(viewLifecycleOwner) {
                findNavController().navigate(R.id.action_global_departmentsFragment)
            }

            deleteContainer.observe(viewLifecycleOwner) {
                showAlertDialog(
                    title = getString(R.string.container_deleted_alert_title),
                    message = getString(R.string.container_deleted_alert_message),
                    negativeMessage = getString(R.string.container_deleted_alert_button),
                    onNegative = { onResume() }
                )
            }

            updateTemperature.observe(viewLifecycleOwner) {
                showAlertDialog(
                    title = getString(R.string.container_deleted_alert_title),
                    message = getString(R.string.container_updated_alert_message),
                    negativeMessage = getString(R.string.container_deleted_alert_button),
                    onNegative = { onResume() }
                )
            }

            btnStopVisibility.observe(viewLifecycleOwner) {
                // TODO: create button in xml and then -> binding.btnStop.visibility = it
            }
        }
    }
}