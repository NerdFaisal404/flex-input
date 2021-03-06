package com.lytefast.flexinput.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.lytefast.flexinput.FlexInputCoordinator;
import com.lytefast.flexinput.R;
import com.lytefast.flexinput.adapters.AddContentPagerAdapter;
import com.lytefast.flexinput.model.Attachment;
import com.lytefast.flexinput.utils.FileUtils;
import com.lytefast.flexinput.utils.SelectionAggregator;
import com.lytefast.flexinput.utils.SelectionCoordinator;


/**
 * Full screen dialog with a {@link ViewPager} as a bottom sheet.
 *
 * @author Sam Shih
 */
public class AddContentDialogFragment extends AppCompatDialogFragment {

  public static final int REQUEST_FILES = 5968;

  private ViewPager contentPager;
  private TabLayout contentTabs;
  private FloatingActionButton actionButton;
  private ImageView launchButton;

  private SelectionAggregator<Attachment<?>> selectionAggregator;


  @Override
  public Dialog onCreateDialog(final Bundle savedInstanceState) {
    AppCompatDialog dialog = new AppCompatDialog(getContext(), R.style.FlexInput_DialogWhenLarge) {
      @Override
      public void show() {
        super.show();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      }
    };
    dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

    Window window = dialog.getWindow();
    window.setWindowAnimations(android.support.design.R.style.Animation_AppCompat_Dialog);
    window.setBackgroundDrawableResource(android.R.color.transparent);

    return dialog;
  }

  @Override
  public int show(final FragmentTransaction transaction, final String tag) {
    transaction.setCustomAnimations(
        android.support.design.R.anim.abc_grow_fade_in_from_bottom,
        android.support.design.R.anim.abc_shrink_fade_out_from_bottom);
    final int commitId = super.show(transaction, tag);
    return commitId;
  }

  @Override
  public void onStart() {
    super.onStart();
    animateIn();
  }

  @Override
  public View onCreateView(final LayoutInflater inflater,
                           @Nullable final ViewGroup container,
                           @Nullable final Bundle savedInstanceState) {
    View root = inflater.inflate(R.layout.dialog_add_content_pager_with_fab, container, false);
    root.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        onContentRootClick();
      }
    });

    contentPager = root.findViewById(R.id.content_pager);
    contentTabs = root.findViewById(R.id.content_tabs);
    actionButton = root.findViewById(R.id.action_btn);
    launchButton = root.findViewById(R.id.launch_btn);
    launchButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        launchFileChooser();
      }
    });

    Fragment parentFragment = getParentFragment();
    if (parentFragment instanceof FlexInputFragment) {
      final FlexInputFragment flexInputFragment = (FlexInputFragment) parentFragment;
      initContentPages(
          new AddContentPagerAdapter(getChildFragmentManager(), flexInputFragment.getContentPages()));

      actionButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
          dismissWithAnimation();
          flexInputFragment.onSend();
        }
      });

      this.selectionAggregator = flexInputFragment.getSelectionAggregator()
          .addItemSelectionListener(itemSelectionListener);
    }

    return root;
  }

  @Override
  public void onResume() {
    super.onResume();
    actionButton.post(new Runnable() {
      @Override
      public void run() {
        updateActionButton();
      }
    });
  }

  @Override
  public void onDestroyView() {
    if (itemSelectionListener != null && selectionAggregator != null) {
      selectionAggregator.removeItemSelectionListener(itemSelectionListener);
    }
    super.onDestroyView();
  }

  public void dismissWithAnimation() {
    animateOut().setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(final Animation animation) {
      }

      @Override
      public void onAnimationEnd(final Animation animation) {
        dismiss();
      }

      @Override
      public void onAnimationRepeat(final Animation animation) {
      }
    });
  }

  void onContentRootClick() {
    if (isCancelable()) {  // TODO check setCanceledOnTouchOutside
      dismissWithAnimation();
    }
  }

  protected AddContentDialogFragment initContentPages(@NonNull final AddContentPagerAdapter pagerAdapter) {
    pagerAdapter.initTabs(getContext(), contentTabs);
    contentPager.setAdapter(pagerAdapter);
    synchronizeTabAndPagerEvents();
    return this;
  }

  private void synchronizeTabAndPagerEvents() {
    contentTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      /**
       * Special cases the first item (keyboard) by closing the pager and opening the keyboard on click.
       */
      @Override
      public void onTabSelected(final TabLayout.Tab tab) {
        int tabPosition = tab.getPosition();
        if (tabPosition == 0) {
          dismissWithAnimation();
          return;
        }
        contentPager.setCurrentItem(tabPosition - 1);
      }

      @Override
      public void onTabUnselected(final TabLayout.Tab tab) {
      }

      @Override
      public void onTabReselected(final TabLayout.Tab tab) {
      }
    });

    contentPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(final int position) {
        contentTabs.getTabAt(position + 1).select();
      }

      @Override
      public void onPageScrollStateChanged(final int state) {
      }
    });
    // set the default to the first real tab
    contentTabs.getTabAt(1).select();
  }

  private SelectionCoordinator.ItemSelectionListener itemSelectionListener =
      new SelectionCoordinator.ItemSelectionListener() {
        @Override
        public void onItemSelected(final Object item) {
          updateActionButton();
        }

        @Override
        public void onItemUnselected(final Object item) {
          updateActionButton();
        }

        @Override
        public void unregister() { }
      };

  public void launchFileChooser() {
    final Intent imagePickerIntent = new Intent(Intent.ACTION_PICK)
        .setType("image/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

    final List<Intent> allIntents = getAllIntents();
    final Parcelable[] intentArray = new Parcelable[allIntents.size()];
    allIntents.toArray(intentArray);

    Intent chooserIntent = Intent.createChooser(imagePickerIntent, getLauncherString())
        .putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
    startActivityForResult(chooserIntent, REQUEST_FILES);
  }

  protected List<Intent> getAllIntents() {
    final String[] mimetypes = {"text/*", "image/*", "video/*"};
    List<ResolveInfo> resolveInfos = getContext().getPackageManager()
        .queryIntentActivities(
            new Intent(Intent.ACTION_GET_CONTENT)
                .setType("application/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, mimetypes)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
            0);

    List<Intent> intents = new ArrayList<>();
    for (ResolveInfo resolveInfo : resolveInfos) {
      final ComponentName componentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        intents.add(new Intent(Intent.ACTION_GET_CONTENT)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setComponent(componentName)
            .setPackage(resolveInfo.activityInfo.packageName));
    }
    final Intent googleDriveIntent = getGoogleDriveIntent();
    if (googleDriveIntent != null) {
      intents.add(googleDriveIntent);
    }
    return intents;
  }

  /**
   * HACK: sigh. If you want to open up google drive file picker without pulling in the
   * google play drive libraries, this is the only way. For some reason gDrive doesn't
   * register as a when you try to perform a normal Intent.ACTION_PICK with any sort of filters.
   * It could be that google wants people to rely on the DocumentsProvider and system file picker.
   * However the system file picker doesn't auto handle virutal files so this is a workaround.
   *
   * @return Intent to open google drive file picker. Null if not found.
   */
  @Nullable
  private Intent getGoogleDriveIntent() {
    List<ResolveInfo> resolveInfos = getContext().getPackageManager()
        .queryIntentActivities(
            new Intent(Intent.ACTION_PICK)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
            0);

    for (ResolveInfo resolveInfo : resolveInfos) {
      final ComponentName componentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);

      if (resolveInfo.activityInfo.name.equals("com.google.android.apps.docs.app.PickActivity")) {
        return new Intent(Intent.ACTION_PICK)
            .setComponent(componentName)
            .setPackage(resolveInfo.activityInfo.packageName);
      }
    }
    return null;
  }

  @Override
  public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (REQUEST_FILES != requestCode || Activity.RESULT_CANCELED == resultCode) {
      return;
    }

    if (Activity.RESULT_OK != resultCode) {
      Toast.makeText(getContext(), "Error loading files", Toast.LENGTH_SHORT).show();
      return;
    }

    ClipData clipData = data.getClipData();

    FlexInputCoordinator<Attachment> flexInputCoordinator = (FlexInputCoordinator<Attachment>) getParentFragment();
    if (clipData == null) {
      Uri uri = data.getData();
      flexInputCoordinator.addExternalAttachment(toAttachment(uri));
    } else {
      for (int i = 0; i < clipData.getItemCount(); i++) {
        ClipData.Item item = clipData.getItemAt(i);
        flexInputCoordinator.addExternalAttachment(toAttachment(item.getUri()));
      }
    }
  }

  @NonNull
  private Attachment toAttachment(final Uri uri) {
    final ContentResolver contentResolver = getContext().getContentResolver();
    return new Attachment(
        uri.hashCode(), uri, FileUtils.getFileName(uri, contentResolver), null);
  }

  //region Animation methods

  private Animation animateOut() {
    Animation animation = AnimationUtils.loadAnimation(
        getContext(), android.support.design.R.anim.design_bottom_sheet_slide_out);
    animation.setDuration(getResources()
        .getInteger(android.support.design.R.integer.bottom_sheet_slide_duration));
    animation.setInterpolator(getContext(), android.R.anim.accelerate_decelerate_interpolator);

    actionButton.hide();
    contentTabs.startAnimation(animation);
    contentPager.startAnimation(animation);
    launchButton.startAnimation(animation);

    return animation;
  }

  private Animation animateIn() {
    Animation animation = AnimationUtils.loadAnimation(
        getContext(), android.support.design.R.anim.design_bottom_sheet_slide_in);
    animation.setDuration(getResources()
        .getInteger(android.support.design.R.integer.bottom_sheet_slide_duration));
    animation.setInterpolator(getContext(), android.R.anim.accelerate_decelerate_interpolator);

    contentTabs.startAnimation(animation);
    contentPager.startAnimation(animation);
    launchButton.startAnimation(animation);
    return animation;
  }

  //endregion

  private void updateActionButton() {
    if (actionButton == null) {
      return;  // Fragment gone, nothing to do
    }

    actionButton.post(new Runnable() {
      @Override
      public void run() {
        if (selectionAggregator.getSize() > 0) {
          actionButton.show();
        } else {
          actionButton.hide();
        }
      }
    });
  }

  private CharSequence getLauncherString() {
    final CharSequence customString;

    final TypedValue value = new TypedValue();
    final Resources.Theme dialogTheme = getDialog().getContext().getTheme();
    if (!dialogTheme.resolveAttribute(R.attr.flexInputAddContentLauncherTitle, value, true)) {
      customString = null;
    } else {
      customString = value.string;
    }
    return TextUtils.isEmpty(customString)
        ?  getString(R.string.choose_an_application): customString;
  }
}
