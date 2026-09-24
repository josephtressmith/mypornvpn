package io.searchvpn.app.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.searchvpn.app.R
import io.searchvpn.app.databinding.ItemChatMessageBinding

class ChatAdapter(
    private val onCommandCopied: (String) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val context = itemView.context

            if (message.sender == Sender.USER) {
                binding.userBubbleContainer.visibility = View.VISIBLE
                binding.assistantBubbleContainer.visibility = View.GONE
                binding.userMessageText.text = message.text
            } else {
                binding.userBubbleContainer.visibility = View.GONE
                binding.assistantBubbleContainer.visibility = View.VISIBLE
                binding.assistantMessageText.text = formatMarkdown(message.text)

                if (!message.extractedCommand.isNullOrBlank()) {
                    binding.copyCommandContainer.visibility = View.VISIBLE
                    binding.extractedCommandText.text = message.extractedCommand

                    binding.btnCopyCommand.setOnClickListener {
                        copyToClipboard(context, message.extractedCommand, "Command copied! Paste into Termux")
                        onCommandCopied(message.extractedCommand)
                    }
                } else {
                    binding.copyCommandContainer.visibility = View.GONE
                }

                binding.btnCopyEntireMessage.setOnClickListener {
                    copyToClipboard(context, message.text, "Response copied to clipboard")
                }
            }
        }

        private fun formatMarkdown(raw: String): CharSequence {
            // Basic clean markdown formatting for readable Android display
            return raw
                .replace("### ", "")
                .replace("## ", "")
                .replace("# ", "")
                .replace("**", "")
                .replace("```bash", "")
                .replace("```sh", "")
                .replace("```", "")
        }

        private fun copyToClipboard(context: Context, text: String, label: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SearchVPN AI", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }
    }
}
