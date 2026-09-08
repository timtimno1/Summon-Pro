package com.justjdupuis.summonpro

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.justjdupuis.summonpro.databinding.FragmentWelcomeBinding
import com.justjdupuis.summonpro.utils.TokenStore
import com.justjdupuis.summonpro.utils.TokenMetadata
import kotlinx.coroutines.launch

class WelcomeFragment : Fragment() {
    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener { showTokenImportDialog() }
        binding.termsText.text = "Personal mode connects directly to Tesla Fleet API. Tokens stay on this device."
    }

    private fun showTokenImportDialog() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        val tokenInput = EditText(requireContext()).apply {
            hint = "Tesla Fleet API access token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val expiryInput = EditText(requireContext()).apply {
            hint = "Valid for hours"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("8")
        }
        container.addView(tokenInput)
        container.addView(expiryInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Import personal access token")
            .setMessage("Generate a token with your own Tesla developer application. It is stored only on this device.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val token = tokenInput.text.toString().trim()
                val hours = expiryInput.text.toString().toLongOrNull()
                if (token.isBlank()) {
                    tokenInput.error = "Token is required"
                    return@setOnClickListener
                }
                if (hours == null || hours !in 1..24) {
                    expiryInput.error = "Enter 1 to 24 hours"
                    return@setOnClickListener
                }
                val enteredLifetime = hours * 60 * 60
                val lifetime = TokenMetadata.expiresInSeconds(token)
                    ?.coerceAtMost(enteredLifetime)
                    ?: enteredLifetime
                TokenStore.savePersonalAccessToken(requireContext(), token, lifetime)
                dialog.dismiss()
                findNavController().navigate(R.id.action_WelcomeFragment_to_VehicleList)
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val token = TokenManager.getValidAccessToken(requireContext())
            if (token != null) {
                findNavController().navigate(
                    R.id.VehicleListFragment,
                    null,
                    navOptions {
                        popUpTo(R.id.nav_graph) {
                            inclusive = true
                        }
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
