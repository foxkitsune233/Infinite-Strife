package com.example.douqiuqiu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.config.ConfigLoader
import com.example.douqiuqiu.model.HeroTemplate

/** 选英雄：8 选 1。GridView + BaseAdapter + ViewHolder（沿用课堂教的写法） */
class HeroSelectActivity : AppCompatActivity() {

    private val heroes = mutableListOf<HeroTemplate>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hero_select)

        heroes.addAll(ConfigLoader.loadHeroes(this))

        val grid = findViewById<GridView>(R.id.gridHeroes)
        grid.adapter = HeroAdapter(heroes)

        grid.setOnItemClickListener { _, _, position, _ ->
            val h = heroes[position]
            Toast.makeText(this, "已选择 ${h.name}，进入闯关地图", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra(MapActivity.EXTRA_HERO_ID, h.id)
            startActivity(intent)
        }
    }

    private class HeroAdapter(private val data: List<HeroTemplate>) : BaseAdapter() {

        override fun getCount(): Int = data.size
        override fun getItem(position: Int): HeroTemplate = data[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder: ViewHolder
            val view: View

            if (convertView == null) {
                view = LayoutInflater.from(parent!!.context)
                    .inflate(R.layout.item_hero, parent, false)
                holder = ViewHolder(
                    emoji = view.findViewById(R.id.tvHeroEmoji),
                    name = view.findViewById(R.id.tvHeroName),
                    trait = view.findViewById(R.id.tvHeroTrait)
                )
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val h = getItem(position)
            holder.emoji.text = h.emoji
            holder.name.text = h.name
            holder.trait.text = h.trait
            return view
        }

        private class ViewHolder(
            val emoji: TextView,
            val name: TextView,
            val trait: TextView
        )
    }
}
