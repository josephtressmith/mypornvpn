package io.searchvpn.app.ai

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.searchvpn.app.R
import io.searchvpn.app.databinding.DialogAiAssistantBinding
import io.searchvpn.app.util.KeyboardUtils
import kotlinx.coroutines.launch

class AiAssistantBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAiAssistantBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatAdapter
    private val messageList = mutableListOf<ChatMessage>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatAdapter { command ->
            // Callback when user copies a command
        }

        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.chatRecyclerView.layoutManager = layoutManager
        binding.chatRecyclerView.adapter = adapter

        // Setup Model / Status Header
        if (AiAgentService.hasApiKey()) {
            binding.modelStatusText.text = "Connected: Gemini 3.5 Flash & Galaxy S24 Ultra Agent"
        } else {
            binding.modelStatusText.text = "Offline Expert Mode (Add Gemini key in AI Studio Secrets for live web/AI)"
        }

        // Add initial welcoming message
        if (messageList.isEmpty()) {
            val welcome = ChatMessage(
                id = "welcome",
                sender = Sender.ASSISTANT,
                text = "👋 Welcome to your **SearchVPN AI Agent** on Samsung Galaxy S24 Ultra!\n\n" +
                        "I am here to assist you with anything:\n" +
                        "• Step-by-step **Termux coding commands** (`termux-setup-storage`, `python`, `git`, `ffmpeg`, `mpv`)\n" +
                        "• Locating and managing **video downloads** in Samsung Gallery\n" +
                        "• **WireGuard VPN** privacy & encrypted searches\n" +
                        "• Any coding or general questions you have!\n\n" +
                        "What would you like help with today?",
                extractedCommand = "termux-setup-storage"
            )
            messageList.add(welcome)
            adapter.setMessages(messageList)
        }

        // Action buttons
        binding.btnCloseAssistant.setOnClickListener {
            KeyboardUtils.hideKeyboard(binding.chatInput)
            dismiss()
        }

        binding.btnClearChat.setOnClickListener {
            KeyboardUtils.hideKeyboard(binding.chatInput)
            messageList.clear()
            AiAgentService.conversationHistory.clear()
            val resetMsg = ChatMessage(
                id = "reset",
                sender = Sender.ASSISTANT,
                text = "Conversation cleared. How can I help you today?"
            )
            messageList.add(resetMsg)
            adapter.setMessages(messageList)
        }

        // Prompt Chips
        binding.chipTermuxStorage.setOnClickListener {
            sendPrompt("How do I setup storage in Termux on my Galaxy S24 Ultra?")
        }
        binding.chipWhereDownloads.setOnClickListener {
            sendPrompt("Where are my downloaded videos saved on Galaxy S24 Ultra?")
        }
        binding.chipPythonGit.setOnClickListener {
            sendPrompt("How do I install Python, Git, and start coding in Termux?")
        }
        binding.chipPlayMpv.setOnClickListener {
            sendPrompt("How can I watch downloaded videos in Termux using mpv?")
        }
        binding.chipWireguard.setOnClickListener {
            sendPrompt("How do I configure and turn on WireGuard VPN?")
        }
        binding.chipThemes.setOnClickListener {
            sendPrompt("How do I change themes and custom wallpapers?")
        }

        // Text input send
        binding.btnSendMessage.setOnClickListener {
            handleSubmit()
        }

        binding.chatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                handleSubmit()
                true
            } else {
                false
            }
        }
    }

    private fun handleSubmit() {
        val text = binding.chatInput.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            binding.chatInput.setText("")
            sendPrompt(text)
        }
    }

    private fun sendPrompt(prompt: String) {
        KeyboardUtils.hideKeyboard(binding.chatInput)

        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            sender = Sender.USER,
            text = prompt
        )
        messageList.add(userMessage)
        adapter.addMessage(userMessage)
        binding.chatRecyclerView.scrollToPosition(adapter.itemCount - 1)

        binding.typingIndicatorContainer.visibility = View.VISIBLE

        lifecycleScope.launch {
            val response = AiAgentService.sendMessage(prompt)
            binding.typingIndicatorContainer.visibility = View.GONE
            messageList.add(response)
            adapter.addMessage(response)
            binding.chatRecyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun onDestroyView() {
        _binding?.chatInput?.let { KeyboardUtils.hideKeyboard(it) }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AiAssistantBottomSheetDialog"
    }
}
