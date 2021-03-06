/*
 *  Copyright (C) 2020. Maximilian Keppeler (https://www.maxkeppeler.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

@file:Suppress("unused")

package com.maxkeppeler.sheets.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.maxkeppeler.sheets.core.Sheet
import com.maxkeppeler.sheets.core.layoutmanagers.CustomGridLayoutManager
import com.maxkeppeler.sheets.core.utils.getPrimaryColor
import com.maxkeppeler.sheets.storage.databinding.SheetsStorageBinding
import java.io.File
import java.io.FileFilter


/** Listener that returns the selected file or folder. */
typealias SelectionListener = (file: List<File>) -> Unit

/** Listener that is invoked when the user wants to create a new folder. */
typealias CreateFolderListener = (file: File, listener: (name: String) -> Unit) -> Unit

/**
 * The [StorageSheet] lets you pick a file or folder.
 */
class StorageSheet : Sheet() {

    override val dialogTag = "StorageSheet"

    private lateinit var binding: SheetsStorageBinding
    private lateinit var storageAdapter: StorageAdapter
    private var filter: FileFilter? = null
    private var selectionMode = StorageSelectionMode.FILE
    private var homeLocation: File = Environment.getExternalStorageDirectory().absoluteFile
    private var currentLocation: File? = null
    private var selectedFiles: MutableList<File> = mutableListOf()
    private var listener: SelectionListener? = null
    private var deleteFiles: Boolean = false
    private var createFolderListener: CreateFolderListener? = null
    private var displayMode: FileDisplayMode = FileDisplayMode.HORIZONTAL
    private var fileColumns: Int = 2
    private var multipleChoices = false
    private var minChoices: Int? = null
    private var maxChoices: Int? = null
    private var displayButtons = true
    private var displayMultipleChoicesInfo = false

    private val colorActive by lazy { getPrimaryColor(requireContext()) }

    private val saveAllowed: Boolean
        get() {
            val validMultipleChoice =
                multipleChoices && selectedFiles.size >= minChoices ?: 1
                        && selectedFiles.size <= maxChoices ?: Int.MAX_VALUE
            val validSingleChoice =
                !multipleChoices && (selectedFiles.isNotEmpty() || selectionMode == StorageSelectionMode.FOLDER)
            return validMultipleChoice || validSingleChoice
        }

    /** Set file display mode. */
    fun fileDisplayMode(displayMode: FileDisplayMode) {
        this.displayMode = displayMode
    }

    /** Set amount of columns for the grid to display files. */
    fun fileColumns(@IntRange(from = 1, to = 5) columns: Int) {
        this.fileColumns = columns
    }

    /** Set the selection mode. */
    fun selectionMode(selectionMode: StorageSelectionMode) {
        this.selectionMode = selectionMode
    }

    /** Set the initial selected files. */
    fun selected(vararg file: File) {
        this.selectedFiles.addAll(file)
    }

    /** Set the home location file. */
    fun homeLocation(file: File) {
        this.homeLocation = file
    }

    /** Set the current location file. */
    fun currentLocation(file: File) {
        this.currentLocation = file
    }

    /** Set the file filter. */
    fun filter(filter: FileFilter) {
        this.filter = filter
    }

    /** Show buttons and require a positive button click. */
    fun multipleChoices(multipleChoices: Boolean = true) {
        this.multipleChoices = multipleChoices
    }

    /** Set the minimum amount of (enabled) choices. */
    fun minChoices(@IntRange(from = 1) minChoices: Int) {
        maxChoices?.let { max ->
            if (minChoices > max)
                throw IllegalStateException("The minimum amount of selected files needs to be less or equal to the maximum amount.")
        }
        this.minChoices = minChoices
    }

    /** Set the maximum amount of (enabled) choices. */
    fun maxChoices(maxChoices: Int) {
        minChoices?.let { min ->
            if (maxChoices < min)
                throw IllegalStateException("The maximum amount of selected files needs to be more or equal to the minimum amount.")
        }
        this.maxChoices = maxChoices
    }

    /** Display buttons and require a positive button click. */
    fun displayButtons(displayButtons: Boolean = true) {
        this.displayButtons = displayButtons
    }

    /** Display the hints for allowed minimum and maximum amount of choices and the amount of selected options. */
    fun displayMultipleChoicesInfo(displayMultipleChoicesInfo: Boolean = true) {
        this.displayMultipleChoicesInfo = displayMultipleChoicesInfo
    }

    /** Set a listener that is invoked when the user wants to create a folder. */
    fun onCreateFolder(listener: CreateFolderListener) {
        this.createFolderListener = listener
    }

    /**
     * Set the [SelectionListener].
     *
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(listener: SelectionListener) {
        this.listener = listener
    }

    /**
     * Set the text of the positive button and set the [SelectionListener].
     *
     * @param positiveRes The String resource id for the positive button.
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(@StringRes positiveRes: Int, listener: SelectionListener? = null) {
        this.positiveText = windowContext.getString(positiveRes)
        this.listener = listener
    }

    /**
     *  Set the text of the positive button and set the [SelectionListener].
     *
     * @param positiveText The text for the positive button.
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(positiveText: String, listener: SelectionListener? = null) {
        this.positiveText = positiveText
        this.listener = listener
    }

    /**
     * Set the text and icon of the positive button and set the [SelectionListener].
     *
     * @param positiveRes The String resource id for the positive button.
     * @param drawableRes The drawable resource for the button icon.
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(
        @StringRes positiveRes: Int,
        @DrawableRes drawableRes: Int,
        listener: SelectionListener? = null,
    ) {
        this.positiveText = windowContext.getString(positiveRes)
        this.positiveButtonDrawable = ContextCompat.getDrawable(windowContext, drawableRes)
        this.listener = listener
    }

    /**
     *  Set the text and icon of the positive button and set the [SelectionListener].
     *
     * @param positiveText The text for the positive button.
     * @param drawableRes The drawable resource for the button icon.
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(
        positiveText: String,
        @DrawableRes drawableRes: Int,
        listener: SelectionListener? = null,
    ) {
        this.positiveText = positiveText
        this.positiveButtonDrawable = ContextCompat.getDrawable(windowContext, drawableRes)
        this.listener = listener
    }

    /**
     * Set the text and icon of the positive button and set the [SelectionListener].
     *
     * @param positiveRes The String resource id for the positive button.
     * @param drawable The drawable for the button icon.
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(
        @StringRes positiveRes: Int,
        drawable: Drawable,
        listener: SelectionListener? = null,
    ) {
        this.positiveText = windowContext.getString(positiveRes)
        this.positiveButtonDrawable = drawable
        this.listener = listener
    }

    /**
     *  Set the text and icon of the positive button and set the [SelectionListener].
     *
     * @param positiveText The text for the positive button.
     * @param drawable The drawable for the button icon.
     * @param listener Listener that is invoked when a file or folder was selected.
     */
    fun onPositive(
        positiveText: String,
        drawable: Drawable,
        listener: SelectionListener? = null,
    ) {
        this.positiveText = positiveText
        this.positiveButtonDrawable = drawable
        this.listener = listener
    }

    private val adapterListener = object : StorageAdapterListener {

        override fun select(file: File) {
            if (displayButtons) {
                selectedFiles.clear()
                selectedFiles.add(file)
                validate()
            } else {
                selectedFiles.add(file)
                Handler(Looper.getMainLooper()).postDelayed({
                    listener?.invoke(selectedFiles)
                    dismiss()
                }, 300)
            }
        }

        override fun isSelected(file: File): Boolean =
            selectedFiles.contains(file)

        override fun selectMultipleChoice(file: File) {
            if (!selectedFiles.contains(file)) {
                selectedFiles.add(file)
                validate()
            }
        }

        override fun deselectMultipleChoice(file: File) {
            selectedFiles.remove(file)
            validate()
        }

        override fun isMultipleChoiceSelectionAllowed(file: File): Boolean =
            multipleChoices && selectedFiles.contains(file) || selectedFiles.size < maxChoices ?: Int.MAX_VALUE

        override fun createFolder(file: File) {
            createFolderListener?.invoke(file) { name ->
                storageAdapter.createFolder(name)
            }
        }
    }

    override fun onCreateLayoutView(): View =
        SheetsStorageBinding.inflate(LayoutInflater.from(activity)).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkSetup()
        displayButtonsView(multipleChoices || displayButtons)
        setButtonPositiveListener(::save)

        if (displayMode == FileDisplayMode.VERTICAL && fileColumns == 1) fileColumns = 2
        with(binding.filesRecyclerView) {

            storageAdapter = StorageAdapter(
                requireContext(),
                selectionMode,
                homeLocation,
                currentLocation,
                filter,
                displayMode,
                fileColumns,
                createFolderListener != null,
                multipleChoices,
                adapterListener
            )
            adapter = storageAdapter
            layoutManager = CustomGridLayoutManager(requireContext(), fileColumns, true).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (position == 0 || storageAdapter.getCurrentFiles().isEmpty())
                            fileColumns else 1
                }
            }
        }

        validate(true)
    }

    private fun checkSetup() {

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) throw IllegalStateException("Permission READ_EXTERNAL_STORAGE is required.")

        if (createFolderListener != null && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) throw IllegalStateException("Permission WRITE_EXTERNAL_STORAGE is required to allow user to create folders.")
    }

    private fun save() {

        val files = mutableListOf<File>()
        when {
            !multipleChoices && selectionMode == StorageSelectionMode.FOLDER ->
                files.add(storageAdapter.getCurrentFile())
            else -> files.addAll(selectedFiles)
        }
        listener?.invoke(files)
        dismiss()
    }

    /** Validate if the current selections fulfils the requirements. */
    private fun validate(init: Boolean = false) {

        displayButtonPositive(saveAllowed, !init)

        if (multipleChoices) {
            updateMultipleChoicesInfo()
        }
    }

    private fun updateMultipleChoicesInfo() {

        if (!displayMultipleChoicesInfo) {
            binding.status.root.visibility = View.GONE
            return
        }

        binding.status.root.visibility = View.VISIBLE

        val selected = selectedFiles.size

        val isMinLabelShown = minChoices?.let { min ->
            val lessThanSelected = selected < min
            if (lessThanSelected) {
                binding.status.minimumLabel.text = getString(R.string.sheets_select_at_least_files, min)
                binding.status.minimumLabel.visibility = View.VISIBLE
            } else {
                binding.status.minimumLabel.visibility = View.GONE
            }
            lessThanSelected
        } ?: false

        if (!isMinLabelShown) {
            binding.status.minimumLabel.visibility = View.GONE
        }

        val actualMaximum = maxChoices ?: Int.MAX_VALUE
        binding.status.selectionLabel.setTextColor(colorActive)
        val textSizeSmall = resources.getDimensionPixelSize(R.dimen.sheetsTextSizeBody)
        val textSpan =
            SpannableString(getString(R.string.sheets_current_of_total, selected, actualMaximum)).apply {
                setSpan(
                    AbsoluteSizeSpan(textSizeSmall), selected.toString().length, this.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

        binding.status.selectionLabel.text = textSpan
    }

    /** Build [StorageSheet] and show it later. */
    fun build(ctx: Context, width: Int? = null, func: StorageSheet.() -> Unit): StorageSheet {
        this.windowContext = ctx
        this.width = width
        this.func()
        return this
    }

    /** Build and show [StorageSheet] directly. */
    fun show(ctx: Context, width: Int? = null, func: StorageSheet.() -> Unit): StorageSheet {
        this.windowContext = ctx
        this.width = width
        this.func()
        this.show()
        return this
    }
}