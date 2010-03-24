/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.ase.facade.ui;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import com.google.ase.exception.AseRuntimeException;
import com.google.ase.future.FutureActivityTask;
import com.google.ase.future.FutureIntent;

/**
 * Wrapper class for alert dialog running in separate thread.
 *
 * @author MeanEYE.rcf (meaneye.rcf@gmail.com)
 */
class RunnableAlertDialog extends FutureActivityTask implements RunnableDialog {
  private AlertDialog mDialog;
  private final String mTitle;
  private final String mMessage;
  private FutureIntent mResult;
  private Set<Integer> mResultItems;
  private Activity mActivity;
  private CharSequence[] mItems;
  private int mSelectedItem;
  private boolean mSelectedItems[];
  private int mListType;
  private String mPositiveButtonText;
  private String mNegativeButtonText;
  private String mNeutralButtonText;
  
  public class ListType {
    public static final int MENU = 0;
    public static final int SINGLE_CHOICE = 1;
    public static final int MULTI_CHOICE = 2;
  }

  public RunnableAlertDialog(String title, String message) {
    mTitle = title;
    mMessage = message;
    mListType = ListType.MENU;
    mResultItems = new TreeSet<Integer>();
  }

  public void setPositiveButtonText(String text) {
    mPositiveButtonText = text;
  }

  public void setNegativeButtonText(String text) {
    mNegativeButtonText = text;
  }

  public void setNeutralButtonText(String text) {
    mNeutralButtonText = text;
  }

  /**
   * Set list items.
   *
   * @param items
   */
  public void setItems(JSONArray items) {
    if (mItems == null) {
      mItems = new CharSequence[items.length()];
      for (int i = 0; i < items.length(); i++) {
        try {
          mItems[i] = items.getString(i);
        } catch (JSONException e) {
          throw new AseRuntimeException(e);
        }
      }
      mListType = ListType.MENU;
    }
  }
  
  /**
   * Set single choice items
   * @param items
   * @param selected
   */
  public void setSingleChoiceItems(JSONArray items, int selected) {
    if (mItems == null) {
      setItems(items);
      mSelectedItem = selected;
      mListType = ListType.SINGLE_CHOICE;
    }
  }

  /**
   * Set multi choice items
   * @param items
   * @param selected
   */
  public void setMultiChoiceItems(JSONArray items, JSONArray selected) {
    if (mItems == null) {
      setItems(items);
      if (selected != null) {
        mSelectedItems = new boolean[items.length()];
        Arrays.fill(mSelectedItems, false);
        for (int i = 0; i < selected.length(); i++) {
          try {
            mSelectedItems[selected.getInt(i)] = true;
            mResultItems.add(selected.getInt(i));
          } catch (JSONException e) {
            throw new AseRuntimeException(e);
          }
        }
      }
      mListType = ListType.MULTI_CHOICE;
    }
  }
  
  @Override
  public Dialog getDialog() {
    return mDialog;
  }
  
  public Set<Integer> getSelectedItems() {
    return mResultItems;
  }

  @Override
  public void run(final Activity activity, FutureIntent result) {
    mActivity = activity;
    mResult = result;
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    if (mTitle != null) {
      builder.setTitle(mTitle);
    }
    // Can't display both a message and items. We'll elect to show the items instead.
    if (mMessage != null && mItems == null) {
      builder.setMessage(mMessage);
    }
    if (mItems != null) {
      switch(mListType) {
        // Add single choice menu items to dialog.
        case ListType.SINGLE_CHOICE:
          builder.setSingleChoiceItems(mItems, mSelectedItem, 
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int item) {
                  mResultItems.clear();
                  mResultItems.add(item);
                }
          });
          break;
        // Add multiple choice items to the dialog.
        case ListType.MULTI_CHOICE:
          builder.setMultiChoiceItems(mItems, mSelectedItems,
              new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int item, 
                    boolean isChecked) {
                  if (isChecked) {
                      mResultItems.add(item);
                  } else {
                    if (mResultItems.contains(item))
                      mResultItems.remove(item);
                  }
                }
              });
          break;
        // Add standard, menu-like, items to dialog.
        default:
          builder.setItems(mItems, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              Intent intent = new Intent();
              intent.putExtra("item", item);
              mResult.set(intent);
              // TODO(damonkohler): This leaves the dialog in the UiFacade map of dialogs. Memory leak.
              dialog.dismiss();
              activity.finish();
            }
          });
          break;
      } 
    }
    configureButtons(builder, activity);
    addOnCancelListener(builder, activity);
    mDialog = builder.show();
  }

  private Builder addOnCancelListener(final AlertDialog.Builder builder, final Activity activity) {
    return builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        Intent intent = new Intent();
        intent.putExtra("canceled", true);
        mResult.set(intent);
        // TODO(damonkohler): This leaves the dialog in the UiFacade map of dialogs. Memory leak.
        dialog.dismiss();
        activity.finish();
      }
    });
  }

  private void configureButtons(final AlertDialog.Builder builder, final Activity activity) {
    DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Intent intent = new Intent();
        switch (which) {
          case DialogInterface.BUTTON_POSITIVE:
            intent.putExtra("which", "positive");
            break;
          case DialogInterface.BUTTON_NEGATIVE:
            intent.putExtra("which", "negative");
            break;
          case DialogInterface.BUTTON_NEUTRAL:
            intent.putExtra("which", "neutral");
            break;
        }
        mResult.set(intent);
        // TODO(damonkohler): This leaves the dialog in the UiFacade map of dialogs. Memory leak.
        dialog.dismiss();
        activity.finish();
      }
    };
    if (mNegativeButtonText != null) {
      builder.setNegativeButton(mNegativeButtonText, buttonListener);
    }
    if (mPositiveButtonText != null) {
      builder.setPositiveButton(mPositiveButtonText, buttonListener);
    }
    if (mNeutralButtonText != null) {
      builder.setNeutralButton(mNeutralButtonText, buttonListener);
    }
  }

  @Override
  public void dismissDialog() {
    mDialog.dismiss();
    mActivity.finish();
  }
}
