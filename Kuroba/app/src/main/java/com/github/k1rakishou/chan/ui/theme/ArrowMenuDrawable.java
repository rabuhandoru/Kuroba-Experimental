/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.theme;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.ui.helper.PinHelper;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;

import javax.inject.Inject;

public class ArrowMenuDrawable
        extends Drawable {

    @Inject
    ThemeEngine themeEngine;

    private final Paint mPaint = new Paint();

    // The angle in degress that the arrow head is inclined at.
    private static final float ARROW_HEAD_ANGLE = (float) Math.toRadians(45);
    // The thickness of the bars
    private final float mBarThickness = dp(2f);
    // The length of top and bottom bars when they merge into an arrow
    private final float mTopBottomArrowSize = dp(11.31f);
    // The length of middle bar
    private final float mBarSize = dp(18f);
    // The length of the middle bar when arrow is shaped
    private final float mMiddleArrowSize = dp(16f);
    // The space between bars when they are parallel
    private final float mBarGap = dp(3f);
    // Use Path instead of canvas operations so that if color has transparency, overlapping sections
    // wont look different
    private final Path mPath = new Path();
    // The reported intrinsic size of the drawable.
    private final int mSize = dp(24f);
    // Whether we should mirror animation when animation is reversed.
    private boolean mVerticalMirror = false;
    // The interpolated version of the original progress
    private float mProgress = 0.0f;
    private float padding = dp(2f);

    private String badgeText;
    private boolean badgeHighImportance = false;
    private Paint badgePaint = new Paint();
    private Rect badgeTextBounds = new Rect();

    public ArrowMenuDrawable(Context context) {
        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);

        mPaint.setColor(Color.WHITE);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPaint.setStrokeWidth(mBarThickness);
        badgePaint.setAntiAlias(true);
    }

    public void onThemeChanged() {
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        // Interpolated widths of arrow bars
        final float arrowSize = lerp(mBarSize, mTopBottomArrowSize, mProgress);
        final float middleBarSize = lerp(mBarSize, mMiddleArrowSize, mProgress);
        // Interpolated size of middle bar
        final float middleBarCut = lerp(0, mBarThickness / 2, mProgress);
        // The rotation of the top and bottom bars (that make the arrow head)
        final float rotation = lerp(0, ARROW_HEAD_ANGLE, mProgress);

        // The whole canvas rotates as the transition happens
        final float canvasRotate = lerp(-180, 0, mProgress);
        final float topBottomBarOffset = lerp(mBarGap + mBarThickness, 0, mProgress);
        mPath.rewind();

        final float arrowEdge = -middleBarSize / 2;
        // draw middle bar
        mPath.moveTo(arrowEdge + middleBarCut, 0);
        mPath.rLineTo(middleBarSize - middleBarCut, 0);

        float arrowWidth = arrowSize * (float) Math.cos(rotation);
        float arrowHeight = arrowSize * (float) Math.sin(rotation);

        if (Float.compare(mProgress, 0f) == 0 || Float.compare(mProgress, 1f) == 0) {
            arrowWidth = Math.round(arrowWidth);
            arrowHeight = Math.round(arrowHeight);
        }

        // top bar
        mPath.moveTo(arrowEdge, topBottomBarOffset);
        mPath.rLineTo(arrowWidth, arrowHeight);

        // bottom bar
        mPath.moveTo(arrowEdge, -topBottomBarOffset);
        mPath.rLineTo(arrowWidth, -arrowHeight);

        canvas.save();
        // Rotate the whole canvas if spinning.
        canvas.rotate(canvasRotate * ((mVerticalMirror) ? -1 : 1), bounds.centerX(), bounds.centerY());
        canvas.translate(bounds.centerX(), bounds.centerY());
        canvas.drawPath(mPath, mPaint);

        canvas.restore();

        // Draw a badge over the arrow/menu
        if (badgeText != null) {
            canvas.save();

            float badgeSize = mSize * 0.7f;
            float badgeX = mSize - (badgeSize / 2f);
            float badgeY = badgeSize / 2f;

            if (badgeHighImportance) {
                badgePaint.setColor(themeEngine.getChanTheme().getBookmarkCounterHasRepliesColor());
            } else {
                badgePaint.setColor(0xDD000000);
            }

            canvas.drawCircle(badgeX, badgeY, (badgeSize / 2f) + padding, badgePaint);

            float textSize;
            if (badgeText.length() == 1) {
                textSize = badgeSize * 0.7f;
            } else if (badgeText.length() == 2) {
                textSize = badgeSize * 0.6f;
            } else {
                textSize = badgeSize * 0.5f;
            }

            if (badgeHighImportance) {
                if (ThemeEngine.isDarkColor(themeEngine.getChanTheme().getBookmarkCounterHasRepliesColor())) {
                    badgePaint.setColor(Color.WHITE);
                } else {
                    badgePaint.setColor(Color.BLACK);
                }
            } else {
                badgePaint.setColor(Color.WHITE);
            }

            badgePaint.setTextSize(textSize);
            badgePaint.getTextBounds(badgeText, 0, badgeText.length(), badgeTextBounds);
            
            canvas.drawText(
                    badgeText,
                    badgeX - badgeTextBounds.right / 2f,
                    badgeY - badgeTextBounds.top / 2f,
                    badgePaint
            );
            
            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int i) {
        mPaint.setAlpha(i);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public float getProgress() {
        return mProgress;
    }

    public void setProgress(float progress) {
        if (progress != mProgress) {
            if (Float.compare(progress, 1f) == 0) {
                mVerticalMirror = true;
            } else if (Float.compare(progress, 0f) == 0) {
                mVerticalMirror = false;
            }
            mProgress = progress;
            invalidateSelf();
        }
    }

    public void setBadge(int count, boolean highImportance) {
        if (ChanSettings.isSplitLayoutMode() || ChanSettings.bottomNavigationViewEnabled.get()) {
            badgeText = null;
            return;
        }

        String text = count == 0 ? null : (PinHelper.getShortUnreadCount(count));
        if (badgeHighImportance != highImportance || !TextUtils.equals(text, badgeText)) {
            badgeText = text;
            badgeHighImportance = highImportance;
            invalidateSelf();
        }
    }

    /**
     * Linear interpolate between a and b with parameter t.
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
