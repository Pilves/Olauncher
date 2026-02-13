package app.olauncher.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

class OnboardingFragment : Fragment() {

    private val pageCount = 4
    private var currentPage = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pager = view.findViewById<RecyclerView>(R.id.onboardingPager)
        val dotsContainer = view.findViewById<LinearLayout>(R.id.dotsContainer)
        val btnSkip = view.findViewById<TextView>(R.id.btnSkip)
        val btnGetStarted = view.findViewById<TextView>(R.id.btnGetStarted)

        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        pager.layoutManager = layoutManager
        pager.adapter = OnboardingPagerAdapter()

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(pager)

        // Create dot indicators
        val dotColor = requireContext().getColorFromAttr(R.attr.primaryColor)
        val dots = Array(pageCount) { i ->
            View(requireContext()).apply {
                val size = 8.dpToPx()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 8.dpToPx()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColor)
                }
                alpha = if (i == 0) 1.0f else 0.3f
            }
        }
        for (dot in dots) {
            dotsContainer.addView(dot)
        }

        fun updatePage(position: Int) {
            currentPage = position
            for (i in dots.indices) {
                dots[i].alpha = if (i == position) 1.0f else 0.3f
            }
            val isLastPage = position == pageCount - 1
            btnSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE
            btnGetStarted.visibility = if (isLastPage) View.VISIBLE else View.GONE
        }

        pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val snappedView = snapHelper.findSnapView(layoutManager) ?: return
                    val position = layoutManager.getPosition(snappedView)
                    updatePage(position)
                }
            }
        })

        btnSkip.setOnClickListener {
            findNavController().popBackStack()
        }

        btnGetStarted.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
