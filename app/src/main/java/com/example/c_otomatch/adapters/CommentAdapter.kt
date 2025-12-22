package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Jangan lupa import Glide
import com.example.c_otomatch.R
import com.example.c_otomatch.models.Comment

class CommentAdapter(
    private val comments: MutableList<Comment>,
    private val currentUserId: String?,
    private val onDeleteClicked: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgUserAvatar)
        val userName: TextView = itemView.findViewById(R.id.tvUserName)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        val commentText: TextView = itemView.findViewById(R.id.tvComment)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun getItemCount() = comments.size

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.userName.text = comment.userName
        holder.commentText.text = comment.text
        holder.ratingBar.rating = comment.rating

        // --- UPDATE LOGIC FOTO ---
        if (comment.userPhotoUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(comment.userPhotoUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.imgAvatar)
            holder.imgAvatar.clearColorFilter()
            holder.imgAvatar.setPadding(0, 0, 0, 0)
        } else {
            holder.imgAvatar.setImageResource(R.drawable.ic_person)
            holder.imgAvatar.setColorFilter(android.graphics.Color.parseColor("#555555")) // Balikin abu-abu
            holder.imgAvatar.setPadding(8, 8, 8, 8)
        }

        if (currentUserId != null && comment.userId == currentUserId) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDeleteClicked(comment) }
        } else {
            holder.btnDelete.visibility = View.GONE
        }
    }

    fun removeItem(comment: Comment) {
        val pos = comments.indexOf(comment)
        if (pos != -1) {
            comments.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }

    fun addComment(comment: Comment) {
        comments.add(0, comment)
        notifyItemInserted(0)
    }
}