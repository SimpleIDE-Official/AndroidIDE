/************************************************************************************
 * This file is part of AndroidIDE.
 * 
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
**************************************************************************************/
package com.itsaky.androidide;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.transition.Fade;
import androidx.transition.Slide;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import com.blankj.utilcode.util.ThrowableUtils;
import com.itsaky.androidide.adapters.WidgetGroupItemAdapter;
import com.itsaky.androidide.adapters.WidgetItemAdapter;
import com.itsaky.androidide.app.StudioActivity;
import com.itsaky.androidide.databinding.ActivityDesignerBinding;
import com.itsaky.androidide.layoutinflater.WidgetDragListener;
import com.itsaky.androidide.models.UIWidget;
import com.itsaky.androidide.models.UIWidgetGroup;
import com.itsaky.androidide.utils.Logger;
import com.itsaky.layoutinflater.ILayoutInflater;
import com.itsaky.layoutinflater.IView;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DesignerActivity extends StudioActivity implements WidgetItemAdapter.OnDragStartListener {
    
    private ActivityDesignerBinding mBinding;
    private UIWidgetGroup selectedGroup;

    public static final String KEY_LAYOUT_PATH = "designer_layoutPath";
    public static final String DRAGGING_WIDGET_TAG = "DRAGGING_WIDGET";
    public static final String DRAGGING_WIDGET_MIME = "application/ide_widget";

    private static final Logger LOG = Logger.instance("DesignerActivity");

    private final List<UIWidgetGroup> widgetGroups = new ArrayList<>();

    @Override
    protected View bindLayout() {
        mBinding = ActivityDesignerBinding.inflate(getLayoutInflater());
        return mBinding.getRoot();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(mBinding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final var toggle = new ActionBarDrawerToggle(this, mBinding.getRoot(), mBinding.toolbar, R.string.app_name, R.string.app_name);
        mBinding.getRoot().addDrawerListener(toggle);
        toggle.syncState();
        
        final var extras = getIntent().getExtras();
        final var path = extras.getString(KEY_LAYOUT_PATH, null);
        final var name = path.substring(path.lastIndexOf(File.separator) + 1);

        getSupportActionBar().setTitle(name);

        try {
            final ILayoutInflater inflater = getApp().getLayoutInflater();
            inflater.resetContextProvider(newContextProvider());
            final IView view = inflater.inflatePath(path, mBinding.layoutContainer);
            mBinding.layoutContainer.addView(view.asView());

            mBinding.layoutContainer.setOnDragListener(new WidgetDragListener(this));
        } catch (Throwable th) {
            mBinding.layoutContainer.removeAllViews();
            mBinding.layoutContainer.addView(createErrorText(th));
            
            LOG.error (getString(R.string.err_cannot_inflate_layout));
        }

        setupWidgets();
    }

    private void setupWidgets() {
        final var common = new UIWidgetGroup(getString(R.string.widget_group_android));
        common.addChild(new UIWidget(getString(R.string.widget_button), R.drawable.ic_widget_button, Button.class));
        common.addChild(new UIWidget(getString(R.string.widget_checkbox), R.drawable.ic_widget_checkbox, CheckBox.class));
        common.addChild(new UIWidget(getString(R.string.widget_checked_textview), R.drawable.ic_widget_checked_textview, CheckedTextView.class));
        common.addChild(new UIWidget(getString(R.string.widget_edittext), R.drawable.ic_widget_edittext, EditText.class));
        common.addChild(new UIWidget(getString(R.string.widget_image_button), R.drawable.ic_widget_image, ImageButton.class));
        common.addChild(new UIWidget(getString(R.string.widget_image_view), R.drawable.ic_widget_image, ImageView.class));
        common.addChild(new UIWidget(getString(R.string.widget_progressbar), R.drawable.ic_widget_progress, ProgressBar.class));
        common.addChild(new UIWidget(getString(R.string.widget_radio_button), R.drawable.ic_widget_radio_button, RadioButton.class));
        common.addChild(new UIWidget(getString(R.string.widget_seekbar), R.drawable.ic_widget_seekbar, SeekBar.class));
        common.addChild(new UIWidget(getString(R.string.widget_spinner), R.drawable.ic_widget_spinner, Spinner.class));
        common.addChild(new UIWidget(getString(R.string.widget_textview), R.drawable.ic_widget_textview, TextView.class));

        // IMPORTANT: Do not select any other groups here
        common.setSelected(true);
        this.selectedGroup = common;
        widgetGroups.add(common);

        final var layouts = new UIWidgetGroup(getString(R.string.widget_group_layouts));
        layouts.addChild(new UIWidget(getString(R.string.layout_linear), R.drawable.ic_widget_linear, LinearLayout.class));
        layouts.addChild(new UIWidget(getString(R.string.layout_relative), R.drawable.ic_widget_relative, RelativeLayout.class));

        widgetGroups.add(layouts);

        final var adapter = new WidgetGroupItemAdapter(widgetGroups, this::showWidgetGroup);
        mBinding.groupItems.setAdapter(adapter);

        showWidgetGroup(common);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showWidgetGroup (@NonNull UIWidgetGroup group) {
        final var children = group.getChildren();
        this.mBinding.widgetItems.setAdapter(new WidgetItemAdapter(children, this));
        this.selectedGroup.setSelected(false);
        this.selectedGroup = group;
        this.selectedGroup.setSelected(true);

        TransitionManager.beginDelayedTransition(mBinding.navigation,
                new TransitionSet()
                        .addTransition(new Slide(Gravity.END))
                        .addTransition(new Fade())
        );

        //noinspection ConstantConditions
        this.mBinding.groupItems.getAdapter().notifyDataSetChanged();
    }

    @NonNull
    @Contract(pure = true)
    private ILayoutInflater.ContextProvider newContextProvider() {
        return () -> DesignerActivity.this;
    }
    
    @NonNull
    private TextView createErrorText (Throwable th) {
        final TextView error = new TextView (this);
        error.setText(ThrowableUtils.getFullStackTrace(th));
        error.setLayoutParams(new ViewGroup.LayoutParams (-2, -2));
        error.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        error.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        return error;
    }

    @Override
    public void onDragStarted(View view) {
        mBinding.getRoot().closeDrawer(GravityCompat.START);
    }
}
