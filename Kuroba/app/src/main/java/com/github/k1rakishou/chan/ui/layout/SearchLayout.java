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
package com.github.k1rakishou.chan.ui.layout;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;

import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.k1rakishou.chan.utils.AndroidUtils.requestKeyboardFocus;

public class SearchLayout
        extends LinearLayout {

    @Inject
    ThemeEngine themeEngine;

    private ColorizableEditText searchView;
    private ImageView clearButton;
    private boolean autoRequestFocus = true;

    public SearchLayout(Context context) {
        super(context);
        init();
    }

    public SearchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (!isInEditMode()) {
            Chan.inject(this);
        }
    }

    public void setAutoRequestFocus(boolean request) {
        this.autoRequestFocus = request;
    }

    public void setCallback(final SearchLayoutCallback callback) {
        searchView = new ColorizableEditText(getContext());
        searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchView.setHint(getString(R.string.search_hint));
        searchView.setHintTextColor(themeEngine.getChanTheme().getTextColorHint());
        searchView.setTextColor(themeEngine.getChanTheme().getTextPrimaryColor());
        searchView.setSingleLine(true);
        searchView.setBackgroundResource(0);
        searchView.setPadding(0, 0, 0, 0);
        clearButton = new ImageView(getContext());
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setAlpha(s.length() == 0 ? 0.0f : 1.0f);
                callback.onSearchEntered(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(searchView);
                callback.onSearchEntered(getText());
                return true;
            }
            return false;
        });
        searchView.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                view.postDelayed(() -> hideKeyboard(view), 100);
            } else if (autoRequestFocus) {
                view.postDelayed(() -> requestKeyboardFocus(view), 100);
            }
        });
        LinearLayout.LayoutParams searchViewParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        searchViewParams.gravity = Gravity.CENTER_VERTICAL;
        addView(searchView, searchViewParams);
        searchView.setFocusable(true);

        if (autoRequestFocus) {
            searchView.requestFocus();
        }

        clearButton.setAlpha(0f);
        clearButton.setImageResource(R.drawable.ic_clear_white_24dp);
        clearButton.getDrawable().setTint(themeEngine.getChanTheme().getTextPrimaryColor());
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setOnClickListener(v -> {
            searchView.setText("");
            requestKeyboardFocus(searchView);
        });
        addView(clearButton, dp(48), MATCH_PARENT);
    }

    public void setText(String text) {
        searchView.setText(text);
    }

    public String getText() {
        return searchView.getText().toString();
    }

    public void setCatalogSearchColors() {
        searchView.setTextColor(Color.WHITE);
        searchView.setHintTextColor(0x88ffffff);
        clearButton.getDrawable().setTintList(null);
    }

    public interface SearchLayoutCallback {
        void onSearchEntered(String entered);
    }
}
