package com.dskmusic.web2app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.dskmusic.web2app.databinding.ActivityPixabaySearchBinding
import kotlinx.coroutines.launch
import java.io.IOException

class PixabaySearchActivity : BaseActivity() {

    private lateinit var binding: ActivityPixabaySearchBinding

    override fun useNoActionBar(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPixabaySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.rgFilter.check(R.id.rbIllustration)
        binding.btnSearch.setOnClickListener { performSearch() }
        binding.etQuery.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                performSearch()
                true
            } else {
                false
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun selectedImageType(): String = when (binding.rgFilter.checkedRadioButtonId) {
        R.id.rbPhoto -> "photo"
        R.id.rbVector -> "vector"
        R.id.rbAll -> "all"
        else -> "illustration"
    }

    private fun performSearch() {
        val query = binding.etQuery.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            showEmpty(getString(R.string.pixabay_empty_query))
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = PixabayRepository.search(query, selectedImageType())
                binding.progressBar.visibility = View.GONE
                if (results.isEmpty()) {
                    showEmpty(getString(R.string.pixabay_no_results))
                } else {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.recyclerView.layoutManager = GridLayoutManager(this@PixabaySearchActivity, 3)
                    binding.recyclerView.adapter = PixabayAdapter(results, lifecycleScope) { image ->
                        val data = Intent().putExtra(EXTRA_IMAGE_URL, image.largeImageURL)
                        setResult(RESULT_OK, data)
                        finish()
                    }
                }
            } catch (e: IOException) {
                binding.progressBar.visibility = View.GONE
                showEmpty(getString(R.string.pixabay_search_error))
            }
        }
    }

    private fun showEmpty(message: String) {
        binding.recyclerView.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = message
    }

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
    }
}
