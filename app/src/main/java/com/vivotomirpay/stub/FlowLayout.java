package com.vivotomirpay.stub;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

/**
 * Простейший flow-layout: чипы переносятся на новую строку, когда не помещаются
 * по ширине. Лечит «плывущий» интерфейс на узких экранах, где обычный
 * горизонтальный LinearLayout выталкивает чипы за границы карточки.
 */
public class FlowLayout extends ViewGroup {

    private final int lineSpacing;

    public FlowLayout(Context context, float spacingDp) {
        super(context);
        lineSpacing = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, spacingDp, context.getResources().getDisplayMetrics());
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int maxWidth = Math.max(0,
                MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        int x = 0;
        int y = getPaddingTop();
        int lineHeight = 0;
        int childState = 0;

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            final int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            final int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (x > 0 && x + cw > maxWidth) {
                x = 0;
                y += lineHeight + lineSpacing;
                lineHeight = 0;
            }
            x += cw;
            lineHeight = Math.max(lineHeight, ch);
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }

        y += lineHeight + getPaddingBottom();
        setMeasuredDimension(
                resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSizeAndState(Math.max(y, getSuggestedMinimumHeight()), heightMeasureSpec, childState));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int contentRight = r - l - getPaddingRight();
        final int maxLineWidth = Math.max(0, contentRight - getPaddingLeft());
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            final int cw = child.getMeasuredWidth();
            final int ch = child.getMeasuredHeight();
            final int cwWithMargins = cw + lp.leftMargin + lp.rightMargin;

            if (x > getPaddingLeft() && x + cwWithMargins > getPaddingLeft() + maxLineWidth) {
                x = getPaddingLeft();
                y += lineHeight + lineSpacing;
                lineHeight = 0;
            }
            final int cy = y + lp.topMargin;
            child.layout(x + lp.leftMargin, cy, x + lp.leftMargin + cw, cy + ch);
            x += cwWithMargins;
            lineHeight = Math.max(lineHeight, ch + lp.topMargin + lp.bottomMargin);
        }
    }
}
