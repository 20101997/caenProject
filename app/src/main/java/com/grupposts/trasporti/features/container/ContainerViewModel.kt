package com.grupposts.trasporti.features.container

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.caen.RFIDLibrary.CAENRFIDException
import com.grupposts.domain.models.*
import com.grupposts.domain.models.Container
import com.grupposts.domain.models.Product
import com.grupposts.domain.usecases.*
import com.grupposts.domain.util.SingleLiveEvent
import com.grupposts.trasporti.R
import com.grupposts.trasporti.base.BaseViewModel
import com.grupposts.trasporti.base.ResourceProvider
import com.grupposts.trasporti.features.rfid.ReadTemperature
import com.grupposts.trasporti.features.rfid.models.BleConnectionActivity
import com.grupposts.trasporti.features.rfid.models.RFIDTag
import com.grupposts.trasporti.utils.toStringTwoDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ContainerViewModel @Inject constructor(
    private val getDepartmentProductsUseCase: GetDepartmentProductsUseCase,
    private val createContainerUseCase: CreateContainerUseCase,
    private val editContainerUseCase: EditContainerUseCase,
    private val getContainersUseCase: GetContainersUseCase,
    private val getContainersFromDepartmentUseCase: GetContainersFromDepartmentUseCase,
    private val getContainersToDepartmentUseCase: GetContainersToDepartmentUseCase,
    private val createSignatureUseCase: CreateSignatureUseCase,
    private val deleteContainersUseCase: DeleteContainersUseCase,
    private val addProductsToContainerUseCase: AddProductsToContainerUseCase,
    private val resourceProvider: ResourceProvider,
    private val updateTemperatureUseCase: UpdateTemperatureUseCase

) : BaseViewModel() {

    private val _products = MutableLiveData<List<Product>>()
    val products: LiveData<List<Product>> = _products

    private val _singleContainer = MutableLiveData<Boolean>()
    val singleContainer: LiveData<Boolean> = _singleContainer

    private val _newContainerVisibility = MutableLiveData<Int>()
    val newContainerVisibility: LiveData<Int> = _newContainerVisibility

    private val _signButtonText = MutableLiveData<String>()
    val signButtonText: LiveData<String> = _signButtonText

    private val _closeButtonText = MutableLiveData<String>()
    val closeButtonText: LiveData<String> = _closeButtonText

    private val _navigateToAddContainer = SingleLiveEvent<Unit>()
    val navigateToAddContainer: LiveData<Unit> = _navigateToAddContainer

    private val _navigateToScanContainer = SingleLiveEvent<Unit>()
    val navigateToScanContainer: LiveData<Unit> = _navigateToScanContainer

    private val _navigateToContainers = SingleLiveEvent<Unit>()
    val navigateToContainers: LiveData<Unit> = _navigateToContainers

    private val _navigateToDepartments = SingleLiveEvent<Unit>()
    val navigateToDepartments: LiveData<Unit> = _navigateToDepartments

    private val _containers = MutableLiveData<List<Container>>()
    val containers: LiveData<List<Container>> = _containers

    private val _editButtonLoading = MutableLiveData(false)
    val editButtonLoading: LiveData<Boolean> = _editButtonLoading

    private val _deleteContainer = SingleLiveEvent<Unit>()
    val deleteContainer: LiveData<Unit> = _deleteContainer

    private val _btnStopVisibility = MutableLiveData<Int>()
    val btnStopVisibility: LiveData<Int> = _btnStopVisibility

    private val _updateTemperature = SingleLiveEvent<Unit>()
    val updateTemperature: LiveData<Unit> = _updateTemperature


    private var container: Container = getDefaultContainer()
    private var selectedProducts: ArrayList<Product> = arrayListOf()
    private var continueScanning: Boolean = false

    private var containerStructureTo: Structure? = null

    private var containerNumber: Int = 1

    var actionType: ActionType? = null
        set(value) {
            field = value
            when (value) {
                ActionType.DELIVERY -> {
                    _newContainerVisibility.postValue(View.GONE)
                    _signButtonText.postValue(resourceProvider.getString(R.string.sign_delivery))
                    _closeButtonText.postValue(resourceProvider.getString(R.string.close_delivery))
                }
                ActionType.WITHDRAWAL -> {
                    _newContainerVisibility.postValue(View.VISIBLE)
                    _signButtonText.postValue(resourceProvider.getString(R.string.sign_withdrawal))
                    _closeButtonText.postValue(resourceProvider.getString(R.string.close_withdrawal))
                }
                else -> {
                    _newContainerVisibility.postValue(View.GONE)
                    _signButtonText.postValue(resourceProvider.getString(R.string.sign))
                    _closeButtonText.postValue(resourceProvider.getString(R.string.close))
                }
            }
        }

    fun onSingleContainer() {
        _singleContainer.postValue(true)
        _navigateToScanContainer.call()
    }

    fun onMultipleContainer() {
        _singleContainer.postValue(false)
        _navigateToScanContainer.call()
    }

    fun getContainerNumber(): String = containerNumber.toStringTwoDigits()

    fun onNewContainerClicked() {
        containerNumber = (containers.value?.size ?: containerNumber) + 1
        _navigateToAddContainer.call()
    }

    private fun getDefaultContainer() = Container(
        id = null,
        code = null,
        finalTravelId = null,
        products = null,
        note = null,
        temperatureTracking = 1,
        temperature = null,
        departmentFrom = null,
        departmentTo = null
    )

    fun setContainerId(id: Int?) {
        container = container.copy(id = id)
    }

    fun setContainerCode(code: String?) {
        container = container.copy(code = code)
    }

    fun setContainerDepartmentFrom(departmentId: Int?) {
        container = container.copy(departmentFrom = departmentId)
    }

    fun setContainerDepartmentTo(departmentTo: Department?) {
        container = container.copy(departmentTo = departmentTo)
    }

    fun setContainerStructureTo(selectedTravel: Travel?, selectedJourney: Journey?) {
        selectedTravel?.let { travel ->
            selectedJourney?.let { journey ->
                travel.journeys?.forEachIndexed { index, j ->
                    if (j.id == journey.id && travel.journeys?.indices?.contains((index + 1)) == true) {
                        containerStructureTo = travel.journeys?.get(index + 1)?.structure
                    }
                }
            }
        }
    }

    fun setContainerNote(note: String?) {
        container = container.copy(note = note)
    }

    fun setContainerProducts(products: List<Product>?) {
        container = container.copy(products = products)
    }

    fun setContainerTemperatureTracking(isTracking: Boolean) {
        container = container.copy(
            temperatureTracking = if (isTracking) {
                1
            } else {
                0
            }
        )
    }

    fun getContainerDepartmentTo() = container.departmentTo

    fun getContainerStructureTo() = containerStructureTo

    fun resetContainer() {
        container = getDefaultContainer()
        selectedProducts = arrayListOf()
    }

    fun onProductChecked(product: Product, isChecked: Boolean) {
        if (isChecked) {
            selectedProducts.add(product)
        } else {
            selectedProducts.remove(product)
        }

        container = container.copy(products = selectedProducts)
    }

    fun getProducts(structureId: Int?) {
        viewModelScope.launch {
            setLoading(true)
            val result = try {
                getDepartmentProductsUseCase(structureId)
            } catch (e: Exception) {
                setError(e)
                emptyList()
            }
            _products.postValue(result)
            setLoading(false)
        }
    }

    private fun createContainer(travelId: Int?) {
        viewModelScope.launch {
            setLoading(true)
            try {
                createContainerUseCase(travelId, container)
                if (continueScanning) {
                    _navigateToScanContainer.call()
                } else {
                    _navigateToContainers.call()
                }
            } catch (e: Exception) {
                setError(e)
            }
            setLoading(false)
        }
    }

    fun editContainer(travelId: Int?) {
        viewModelScope.launch {
            _editButtonLoading.value = true
            try {
                editContainerUseCase(travelId, container.id, container)
                addProductsToContainerUseCase(travelId, container.id, container.products)
                _navigateToContainers.call()
            } catch (e: Exception) {
                setError(e)
            }
            _editButtonLoading.value = false
        }
    }

    fun onSaveAndContinue(id: Int?) {
        continueScanning = true
        containerNumber++
        selectedProducts = arrayListOf()
        createContainer(id)
    }

    fun onSaveAndClose(id: Int?) {
        continueScanning = false
        createContainer(id)
    }

    fun getContainers(travelId: Int?) {
        viewModelScope.launch {
            setLoading(true)
            val result = try {
                getContainersUseCase(travelId)
            } catch (e: Exception) {
                setError(e)
                emptyList()
            }
            _containers.postValue(result)
            setLoading(false)
        }
    }

    fun onDeliveryAction(travelId: Int?, departmentTo: Int?) {
        actionType = ActionType.DELIVERY
        _btnStopVisibility.postValue(View.VISIBLE)
        getContainersToDepartment(travelId, departmentTo)
    }

    fun onWithdrawalAction(travelId: Int?) {
        actionType = ActionType.WITHDRAWAL
        _btnStopVisibility.postValue(View.GONE)
        getContainersFromDepartment(travelId, container.departmentFrom)
    }

    private fun getContainersFromDepartment(travelId: Int?, departmentId: Int?) {
        viewModelScope.launch {
            setLoading(true)
            val result = try {
                getContainersFromDepartmentUseCase(travelId, departmentId)
            } catch (e: Exception) {
                setError(e)
                emptyList()
            }
            _containers.postValue(result)
            setLoading(false)
        }
    }

    private fun getContainersToDepartment(travelId: Int?, departmentTo: Int?) {
        viewModelScope.launch {
            setLoading(true)
            val result = try {
                getContainersToDepartmentUseCase(travelId, departmentTo)
            } catch (e: Exception) {
                setError(e)
                emptyList()
            }
            _containers.postValue(result)
            setLoading(false)
        }
    }

    fun createSignature(
        journeyId: Int?,
        departmentId: Int?,
        driverBitmap: Bitmap?,
        departmentBitmap: Bitmap?
    ) {
        viewModelScope.launch {
            try {
                val convertedDriver = convertBitmapToBase64(driverBitmap)
                val convertedDepartment = convertBitmapToBase64(departmentBitmap)
                createSignatureUseCase(
                    signature = convertedDriver,
                    signatureDepartment = convertedDepartment,
                    containerIds = containers.value?.map { it.id ?: -1 },
                    departmentId = departmentId,
                    journeyItemId = journeyId,
                    type = actionType
                )
                _navigateToDepartments.call()
            } catch (e: Exception) {
                setError(e)
            }
        }
    }

    private fun convertBitmapToBase64(bitmap: Bitmap?): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun deleteContainers(travelId: Int?) {
        viewModelScope.launch {
            setLoading(true)
            try {
                containers.value?.let { containers ->
                    deleteContainersUseCase(travelId, containers.map { it.id ?: -1 })
                }
                _navigateToDepartments.call()
            } catch (e: Exception) {
                setError(e)
            }
            setLoading(false)
        }
    }

    fun deleteContainer(travelId: Int?, containerId: Int?) {
        viewModelScope.launch {
            setLoading(true)
            try {
                travelId?.let { travId ->
                    containerId?.let {
                        deleteContainersUseCase(travId, listOf(it))
                    }
                    _deleteContainer.call()
                }
            } catch (e: Exception) {
                setError(e)
            }
            setLoading(false)
        }
    }

    fun getSignatureTitle(): String = when (actionType) {
        ActionType.DELIVERY -> resourceProvider.getString(R.string.signature_title_delivery)
        ActionType.WITHDRAWAL -> resourceProvider.getString(R.string.signature_title_withdrawal)
        else -> resourceProvider.getString(R.string.sign)
    }

    //here update temperature
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    suspend fun readTemperatureFromTag(read: ReadTemperature): Any? {
        return withContext(Dispatchers.Default) {
            val response = read.getTemperatureFromTag(RFIDTag.hexStringToByteArray(read.getmTagHex()),"67","8A")
            return@withContext response
        }
    }


    fun updateTemperatue(travelId: Int?,containerId: Int?, code: String?){

        viewModelScope.launch {
            setLoading(true)
            try {
                // selected reader
                val mReader = BleConnectionActivity.Readers.get(BleConnectionActivity.Selected_Reader).getReader();
                Log.d("rederCaen",mReader.toString());
                Log.d("rederCaen",code.toString());
                val read : ReadTemperature = ReadTemperature (mReader,code)
                //must be async blocking the Ui
                //val m = read.getTemperatureFromTag(RFIDTag.hexStringToByteArray(read.getmTagHex()),"67","8A");
                val m = readTemperatureFromTag(read)
                Log.d("rederCaen",m.toString());
                updateTemperatureUseCase(travelId,containerId, m as List<Temperature>?)
                _updateTemperature.call()
            } catch (e: Exception) {
                setError(e)
            }catch (e: CAENRFIDException){
                setError(e)
            }
            setLoading(false)

/////////////////////////////////////////////////////////////////////////////////////////////////


        }
    }









}