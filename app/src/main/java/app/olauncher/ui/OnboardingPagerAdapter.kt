package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

class OnboardingPagerAdapter : RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    data class Page(val titleRes: Int, val subtitleRes: Int, val featureRes: List<Int>)

    private val pages = listOf(
        Page(
            R.string.onboarding_gestures_title,
            R.string.onboarding_gestures_subtitle,
            listOf(
                R.string.onboarding_gesture_swipe_up,
                R.string.onboarding_gesture_swipe_down,
                R.string.onboarding_gesture_swipe_left,
                R.string.onboarding_gesture_swipe_right,
                R.string.onboarding_gesture_double_tap
            )
        ),
        Page(
            R.string.onboarding_home_title,
            R.string.onboarding_home_subtitle,
            listOf(
                R.string.onboarding_home_slots,
                R.string.onboarding_home_clock,
                R.string.onboarding_home_long_press_app,
                R.string.onboarding_home_long_press_empty
            )
        ),
        Page(
            R.string.onboarding_features_title,
            R.string.onboarding_features_subtitle,
            listOf(
                R.string.onboarding_feature_widgets,
                R.string.onboarding_feature_quick_note,
                R.string.onboarding_feature_focus_mode,
                R.string.onboarding_feature_gesture_letters
            )
        ),
        Page(
            R.string.onboarding_ready_title,
            R.string.onboarding_ready_subtitle,
            listOf(
                R.string.onboarding_ready_themes,
                R.string.onboarding_ready_wallpaper,
                R.string.onboarding_ready_hidden_apps,
                R.string.onboarding_ready_habit
            )
        )
    )

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.pageTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.pageSubtitle)
        private val featuresContainer: LinearLayout = itemView.findViewById(R.id.featuresContainer)

        fun bind(page: Page) {
            val ctx = itemView.context
            title.text = ctx.getString(page.titleRes)
            subtitle.text = ctx.getString(page.subtitleRes)

            featuresContainer.removeAllViews()
            for (featureRes in page.featureRes) {
                val tv = TextView(ctx).apply {
                    text = ctx.getString(featureRes)
                    textSize = 16f
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    setPadding(0, 10.dpToPx(), 0, 10.dpToPx())
                }
                featuresContainer.addView(tv)
            }
        }
    }
}
