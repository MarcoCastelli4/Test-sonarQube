package com.example.leaguepro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.leaguepro.databinding.CardCommunicationsBinding

class CommunicationAdapter(private val communications: List<Communication>) :
    RecyclerView.Adapter<CommunicationAdapter.CommunicationViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunicationViewHolder {
            val binding = CardCommunicationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CommunicationViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CommunicationViewHolder, position: Int) {
            holder.bind(communications[position])
        }

        override fun getItemCount(): Int = communications.size

        class CommunicationViewHolder(private val binding: CardCommunicationsBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(communication: Communication) {
                binding.tvCommunication.text = communication.text
                binding.tvDate.text = communication.date
            }
        }
}