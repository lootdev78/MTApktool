package modder.hub.editor;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import modder.hub.editor.buffer.GapBuffer;
import modder.hub.editor.component.ClipboardPanel;
import modder.hub.editor.listener.OnTextChangedListener;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.universalchardet.UniversalDetector;

public class MainActivity extends ComponentActivity {

    private final String TAG = this.getClass().getSimpleName();

    private EditView editView;

    private ProgressBar mIndeterminateBar;

    private SharedPreferences mSharedPreference;
    private SharedPreferences editor_pref;

    private Charset mDefaultCharset = StandardCharsets.UTF_8;
    private String mLineSeparator = "\n";
    private boolean mFileModifiedManually = false;
    private String externalPath = File.separator;
    private Uri sourceUri = null;
    private String sourceDisplayName = null;

    private EditText edittext_replace, edittext_find;
    private TextView previous_btn, next_btn, replace_btn, replace_all_btn, item_menu;
    private LinearLayout search_pad, linear_rep;

    private FrameLayout editorContainer;
    private LinearLayout functionBar;

    private static final List<String> SYMBOLS = Arrays.asList(
            "(", ")", "[", "]", "{", "}", ".", ",", ";",
            "'", "\"", "+", "-", "*", "/", "%", "=", "<",
            ">", "&", "|", "~", "^", "!", "?", "\\", ":",
            "#", "@", "`"
    );

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            invalidateOptionsMenu();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyHostTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureWindowInsets();
        initialize();
        initializeLogic();
    }

    private void configureWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        final View root = findViewById(R.id.rootLayout);
        final View bottom = findViewById(R.id.linear_bottom_layout);
        final boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), root);
        bars.setAppearanceLightStatusBars(!dark);
        bars.setAppearanceLightNavigationBars(!dark);
        final int baseBottomPadding = bottom.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets statusAndCutout = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(statusAndCutout.left, statusAndCutout.top, statusAndCutout.right, 0);
            Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            bottom.setPadding(
                    bottom.getPaddingLeft(),
                    bottom.getPaddingTop(),
                    bottom.getPaddingRight(),
                    baseBottomPadding + navigation.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyHostTheme() {
        String mode = getSharedPreferences("mtapktool_theme_bridge", MODE_PRIVATE)
                .getString("mode", "SYSTEM");
        boolean dark;
        if ("DARK".equals(mode)) {
            dark = true;
        } else if ("LIGHT".equals(mode)) {
            dark = false;
        } else {
            dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        }
        setTheme(dark ? R.style.MTApktoolEditorTheme_Dark : R.style.MTApktoolEditorTheme_Light);
    }

    private void initialize() {
        mIndeterminateBar = findViewById(R.id.indeterminateBar);
        mIndeterminateBar.setBackground(null);
        editor_pref = getSharedPreferences("editor_pref", MODE_PRIVATE);
        mSharedPreference = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);

        edittext_replace = findViewById(R.id.edittext_replace);
        edittext_find = findViewById(R.id.edittext_find);
        previous_btn = findViewById(R.id.previous_btn);
        next_btn = findViewById(R.id.next_btn);
        replace_btn = findViewById(R.id.replace_btn);
        replace_all_btn = findViewById(R.id.replace_all_btn);
        item_menu = findViewById(R.id.item_menu);
        search_pad = findViewById(R.id.search_pad);
        linear_rep = findViewById(R.id.linear_rep);

        editorContainer = findViewById(R.id.editorContainer);
        functionBar = findViewById(R.id.functionBar);

        editView = new EditView(this);
        editView.setWordWrap(editor_pref.getBoolean("word_wrap", false));
        editView.setAutoCompleteEnabled(editor_pref.getBoolean("auto_complete", true));
        editView.setShowLineNumbers(editor_pref.getBoolean("show_line_numbers", true));
        editView.setStickyLineNumbers(editor_pref.getBoolean("sticky_line_numbers", true));
        editView.setShowIndentGuides(editor_pref.getBoolean("show_indent_guides", true));
        editView.setShowWrapArrows(editor_pref.getBoolean("show_wrap_arrows", true));
        editView.setAutoIndentEnabled(editor_pref.getBoolean("auto_indent", true));
    }

    private void initializeLogic() {
        setTitle("MH Text Editor");
        editView.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
        ));

        editorContainer.addView(editView);

        loadSmaliInstructions();

        addFunctionBar(functionBar, editView);

        editView.setTypeface(Typeface.DEFAULT);
        if (editor_pref.contains("syntax_position")) {
			int pos = editor_pref.getInt("syntax_position", 0);
			if (pos == 0) {
				editView.setSyntaxLanguageFileName(null);
			} else {
				List<SyntaxItem> syntaxList = loadSyntaxList();
				if (pos - 1 < syntaxList.size()) {
					editView.setSyntaxLanguageFileName(syntaxList.get(pos - 1).Path);
				}
			}
		}
        if (editor_pref.contains("menu_style")) {
            if (editor_pref.getInt("menu_style", 0) == 0) {
                editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_AND_TEXT);
            }
            if (editor_pref.getInt("menu_style", 0) == 1) {
                editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.TEXT_ONLY);
            }
            if (editor_pref.getInt("menu_style", 0) == 2) {
                editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_ONLY);
            }
        }

        editView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Log.d(TAG, "beforeTextChanged: " + s.length());
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Log.d(TAG, "onTextChanged: " + s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Log.d(TAG, "afterTextChanged: " + s.length());
            }
        });
        editView.setOnTextChangedListener(new OnTextChangedListener() {
            @Override
            public void onTextChanged() {
                mHandler.sendEmptyMessage(0);
                editView.postInvalidate();
            }
        });
        editView.setOnSelectionChangeListener(new EditView.OnSelectionChangeListener() {
            @Override
            public void onSelectionChanged(int start, int end) {
                mHandler.sendEmptyMessage(0);
            }
        });
        Intent launchIntent = getIntent();
        String directPath = launchIntent != null ? launchIntent.getStringExtra("path") : null;
        String directUri = launchIntent != null ? launchIntent.getStringExtra("uri") : null;
        sourceDisplayName = launchIntent != null ? launchIntent.getStringExtra("name") : null;
        if (directUri != null && !directUri.isEmpty()) {
            try {
                sourceUri = Uri.parse(directUri);
                File temp = new File(getCacheDir(), "editor-" + System.nanoTime() + "-" +
                        (sourceDisplayName == null ? "document.txt" : sourceDisplayName.replace('/', '_')));
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     FileOutputStream out = new FileOutputStream(temp)) {
                    if (in == null) throw new IOException("Cannot open document");
                    byte[] buffer = new byte[1024 * 256];
                    int read;
                    while ((read = in.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
                }
                mSharedPreference.edit().putString("path", temp.getAbsolutePath()).apply();
                setTitle(sourceDisplayName == null ? "Text Editor" : sourceDisplayName);
                readFileAsync(temp.getAbsolutePath());
            } catch (Exception error) {
                Toast.makeText(this, error.getMessage() == null ? "Open failed" : error.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (directPath != null && !directPath.isEmpty() && new File(directPath).isFile()) {
            mSharedPreference.edit().putString("path", directPath).apply();
            setTitle(new File(directPath).getName());
            readFileAsync(directPath);
        } else if (mSharedPreference.contains("path")) {
            String path = mSharedPreference.getString("path", "");
            if (new File(path).exists()) {
                setTitle(new File(path).getName());
                readFileAsync(path);
            }
        }

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            externalPath = resolveSharedStorageRoot().getAbsolutePath();
        }
    }

    private void loadSmaliInstructions() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream is = getAssets().open("smali_instructions.json");
                    int size = is.available();
                    byte[] buffer = new byte[size];
                    int bytesRead = is.read(buffer);
                    is.close();
                    if (bytesRead > 0) {
                        String json = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        final org.json.JSONArray array = new org.json.JSONArray(json);
                        final Set<String> instructions = new HashSet<String>();
                        for (int i = 0; i < array.length(); i++) {
                            instructions.add(array.getString(i));
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (editView != null) {
                                    editView.setInstructions(instructions);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading Smali instructions", e);
                }
            }
        }).start();
    }

    private void toggleEditMode() {
        editView.setEditedMode(!editView.getEditedMode());
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO: Implement this method
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem moreMenu = menu.findItem(R.id.moreItems);
        moreMenu.getIcon().setTint(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
        MenuItem saveMenu = menu.findItem(R.id.save);
        MenuItem undo = menu.findItem(R.id.undo);
        undo.setIcon(R.drawable.ic_undo);
        if (editView.canUndo() || mFileModifiedManually) {
            saveMenu.getIcon().setTint(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
            saveMenu.setEnabled(true);
        } else {
            saveMenu.getIcon().setTint(themeColor(android.R.attr.textColorSecondary, Color.GRAY));
            saveMenu.setEnabled(false);
        }

        if (editView.canUndo()) {
            undo.getIcon().setTint(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
            undo.setEnabled(true);
        } else {
            undo.getIcon().setTint(themeColor(android.R.attr.textColorSecondary, Color.GRAY));
            undo.setEnabled(false);
        }
        MenuItem redo = menu.findItem(R.id.redo);
        redo.setIcon(R.drawable.ic_redo);
        if (editView.canRedo()) {
            redo.getIcon().setTint(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
            redo.setEnabled(true);
        } else {
            redo.getIcon().setTint(themeColor(android.R.attr.textColorSecondary, Color.GRAY));
            redo.setEnabled(false);
        }

        // Line Break selection
        if (mLineSeparator.equals("\n")) menu.findItem(R.id.eol_unix).setChecked(true);
        else if (mLineSeparator.equals("\r\n")) menu.findItem(R.id.eol_windows).setChecked(true);
        else if (mLineSeparator.equals("\r")) menu.findItem(R.id.eol_mac).setChecked(true);

        // Encoding selection
        String charsetName = mDefaultCharset.name().toUpperCase();
        if (charsetName.contains("UTF-8")) menu.findItem(R.id.enc_utf8).setChecked(true);
        else if (charsetName.contains("UTF-16LE")) menu.findItem(R.id.enc_utf16le).setChecked(true);
        else if (charsetName.contains("UTF-16BE")) menu.findItem(R.id.enc_utf16be).setChecked(true);
        else if (charsetName.contains("GBK")) menu.findItem(R.id.enc_gbk).setChecked(true);
        else if (charsetName.contains("BIG5")) menu.findItem(R.id.enc_big5).setChecked(true);
        else if (charsetName.contains("1251")) menu.findItem(R.id.enc_win1251).setChecked(true);
        else if (charsetName.contains("1252")) menu.findItem(R.id.enc_win1252).setChecked(true);
        else if (charsetName.contains("1258")) menu.findItem(R.id.enc_win1258).setChecked(true);

        MenuItem editMode = menu.findItem(R.id.read_only);

        if (editView.getEditedMode()) {
            editMode.setChecked(false);
        } else {
            editMode.setChecked(true);
        }

        MenuItem wordWrap = menu.findItem(R.id.word_wrap);
        wordWrap.setChecked(editView.isWordWrap());

        MenuItem autoComplete = menu.findItem(R.id.auto_complete);
        autoComplete.setChecked(editView.isAutoCompleteEnabled());

        MenuItem showLineNumbers = menu.findItem(R.id.show_line_numbers);
        showLineNumbers.setChecked(editView.isShowLineNumbers());

        MenuItem stickyLineNumbers = menu.findItem(R.id.sticky_line_numbers);
        stickyLineNumbers.setChecked(editView.isStickyLineNumbers());

        MenuItem showIndentGuides = menu.findItem(R.id.show_indent_guides);
        showIndentGuides.setChecked(editView.isShowIndentGuides());

        MenuItem showWrapArrows = menu.findItem(R.id.show_wrap_arrows);
        showWrapArrows.setChecked(editView.isShowWrapArrows());

        MenuItem autoIndent = menu.findItem(R.id.auto_indent);
        autoIndent.setChecked(editView.isAutoIndentEnabled());

        MenuItem prevPos = menu.findItem(R.id.prev_pos);
        prevPos.setEnabled(editView.canGoBack());
        if (prevPos.getIcon() != null) {
            prevPos.getIcon().setAlpha(editView.canGoBack() ? 255 : 128);
        }

        MenuItem nextPos = menu.findItem(R.id.next_pos);
        nextPos.setEnabled(editView.canGoForward());
        if (nextPos.getIcon() != null) {
            nextPos.getIcon().setAlpha(editView.canGoForward() ? 255 : 128);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.undo) {
            editView.undo();
        } else if (id == R.id.search) {
            searchPanel();
        } else if (id == R.id.redo) {
            editView.redo();
        } else if (id == R.id.read_only) {
            search_pad.setVisibility(View.GONE);
            toggleEditMode();
        } else if (id == R.id.word_wrap) {
            editView.setWordWrap(!editView.isWordWrap());
            editor_pref.edit().putBoolean("word_wrap", editView.isWordWrap()).apply();
        } else if (id == R.id.auto_complete) {
            editView.setAutoCompleteEnabled(!editView.isAutoCompleteEnabled());
            editor_pref.edit().putBoolean("auto_complete", editView.isAutoCompleteEnabled()).apply();
        } else if (id == R.id.show_line_numbers) {
            editView.setShowLineNumbers(!editView.isShowLineNumbers());
            editor_pref.edit().putBoolean("show_line_numbers", editView.isShowLineNumbers()).apply();
        } else if (id == R.id.sticky_line_numbers) {
            editView.setStickyLineNumbers(!editView.isStickyLineNumbers());
            editor_pref.edit().putBoolean("sticky_line_numbers", editView.isStickyLineNumbers()).apply();
        } else if (id == R.id.show_indent_guides) {
            editView.setShowIndentGuides(!editView.isShowIndentGuides());
            editor_pref.edit().putBoolean("show_indent_guides", editView.isShowIndentGuides()).apply();
        } else if (id == R.id.show_wrap_arrows) {
            editView.setShowWrapArrows(!editView.isShowWrapArrows());
            editor_pref.edit().putBoolean("show_wrap_arrows", editView.isShowWrapArrows()).apply();
        } else if (id == R.id.auto_indent) {
            editView.setAutoIndentEnabled(!editView.isAutoIndentEnabled());
            editor_pref.edit().putBoolean("auto_indent", editView.isAutoIndentEnabled()).apply();
        } else if (id == R.id.eol_unix) {
            mLineSeparator = "\n";
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.eol_windows) {
            mLineSeparator = "\r\n";
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.eol_mac) {
            mLineSeparator = "\r";
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_utf8) {
            mDefaultCharset = StandardCharsets.UTF_8;
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_utf16le) {
            mDefaultCharset = Charset.forName("UTF-16LE");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_utf16be) {
            mDefaultCharset = Charset.forName("UTF-16BE");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_gbk) {
            mDefaultCharset = Charset.forName("GBK");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_big5) {
            mDefaultCharset = Charset.forName("Big5");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_win1251) {
            mDefaultCharset = Charset.forName("windows-1251");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_win1252) {
            mDefaultCharset = Charset.forName("windows-1252");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.enc_win1258) {
            mDefaultCharset = Charset.forName("windows-1258");
            mFileModifiedManually = true;
            mHandler.sendEmptyMessage(0);
        } else if (id == R.id.prev_pos) {
            editView.goBack();
        } else if (id == R.id.next_pos) {
            editView.goForward();
        } else if (id == R.id.openFile) {
            showOpenFileDialog();
        } else if (id == R.id.gotoLine) {
            showGotoLineDialog();
        } else if (id == R.id.changeSyntax) {
            _syntaxSelection();
        } else if (id == R.id.preference) {
            menuStyle();
        } else if (id == R.id.save) {
            String path = mSharedPreference.getString("path", "");
            if (!path.isEmpty()) {
                writeFileAsync(path);
            }
        } else if (id == R.id.delete_line) {
            editView.deleteLine();
            return true;
        } else if (id == R.id.empty_line) {
            editView.emptyLine();
            return true;
        } else if (id == R.id.replace_line) {
            editView.replaceLine();
            return true;
        } else if (id == R.id.duplicate_line) {
            editView.duplicateLine();
            return true;
        } else if (id == R.id.toggle_comment) {
            editView.toggleComment();
            return true;
        } else if (id == R.id.copy_line) {
            editView.copyLine();
            return true;
        } else if (id == R.id.cut_line) {
            editView.cutLine();
            return true;
        } else if (id == R.id.convert_uppercase) {
            editView.convertSelectionToUpperCase();
            return true;
        } else if (id == R.id.convert_lowercase) {
            editView.convertSelectionToLowerCase();
            return true;
        } else if (id == R.id.increase_indent) {
            editView.increaseIndent();
            return true;
        } else if (id == R.id.decrease_indent) {
            editView.decreaseIndent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void _syntaxSelection() {
		final List<SyntaxItem> syntaxList = loadSyntaxList();
		
		List<String> display = new ArrayList<>();
		display.add("Text");
		
		for (SyntaxItem item : syntaxList) {
			display.add(item.Syntax);
		}
		
		String[] items = display.toArray(new String[0]);
		int checkedItem = editor_pref.getInt("syntax_position", 0);
		
		AlertDialog.Builder d = new AlertDialog.Builder(this);
		d.setTitle("Syntax");
		
		d.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					_savePosition(which, "syntax_position");
					if (which == 0) {
						// Text mode (no syntax)
						editView.setSyntaxLanguageFileName(null);
					} else {
						// which - 1 because the first item is "Text"
						SyntaxItem selected = syntaxList.get(which - 1);
						editView.setSyntaxLanguageFileName(selected.Path);
					}
					dialog.dismiss();
				}
			});

		d.setPositiveButton("Close", null);
		d.show();
	}

    public void menuStyle() {
        final AlertDialog.Builder d_build = new AlertDialog.Builder(MainActivity.this);
        d_build.setTitle("Floating Menu Style");
        String[] items = {"Show all", "Show title only", "Show icon only"};
        int checkedItem = (int) _getMenuStyle();
        d_build.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                _savePosition((double) which, "menu_style");
                switch (which) {
                    case 0:
                        editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_AND_TEXT);
                        break;
                    case 1:
                        editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.TEXT_ONLY);
                        break;
                    case 2:
                        editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_ONLY);
                        break;
                }
                dialog.dismiss();
            }
        });
        d_build.setPositiveButton("Close", null);
        d_build.show();
    }

    public double _getThemePosition() {
        if (editor_pref.contains("syntax_position")) {
            return ((double) editor_pref.getInt("syntax_position", 0));
        } else {
            return (0);
        }
    }

    public double _getMenuStyle() {
        if (editor_pref.contains("menu_style")) {
            return ((double) editor_pref.getInt("menu_style", 0));
        } else {
            return (2);
        }
    }

    public void _savePosition(final double _position, String name) {
        SharedPreferences.Editor editor = editor_pref.edit();
        editor.putInt(name, (int) _position);
        editor.apply();
    }

    private void showGotoLineDialog() {
        final View v = getLayoutInflater().inflate(R.layout.dialog_gotoline, null);
        final EditText lineEdit = v.findViewById(R.id.lineEdit);
        lineEdit.setHint("1.." + editView.getLineCount());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("goto line");

        builder.setPositiveButton("goto", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dia, int which) {
                String line = lineEdit.getText().toString();
                if (!line.isEmpty()) {
                    editView.gotoLine(Integer.parseInt(line));
                }
            }
        });

        builder.setCancelable(true).show();
    }

    private void showOpenFileDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_openfile, null);
        final EditText pathEdit = v.findViewById(R.id.pathEdit);
        String path = mSharedPreference.getString("path", "");
        if (path.isEmpty())
            pathEdit.setHint("please enter the file path");
        else
            pathEdit.setText(path);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("open file");

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dia, int which) {
                String pathname = pathEdit.getText().toString();
                if (!pathname.isEmpty()) {
                    mSharedPreference.edit().putString("path", pathname).commit();
                    readFileAsync(pathname);
                }
            }
        });
        builder.setCancelable(true).show();
    }

    // Read/write on a dedicated executor. AsyncTask was removed because it is deprecated.
    private void readFileAsync(final String path) {
        editView.setEditedMode(false);
        mHandler.sendEmptyMessage(0);
        mIndeterminateBar.setVisibility(View.VISIBLE);
        ioExecutor.execute(() -> {
            GapBuffer loadedBuffer = null;
            boolean success = false;
            try {
                File file = new File(path);
                String charset = UniversalDetector.detectCharset(file);
                if (charset != null) {
                    try {
                        mDefaultCharset = Charset.forName(charset);
                    } catch (Exception ignored) {
                        mDefaultCharset = StandardCharsets.UTF_8;
                    }
                } else {
                    mDefaultCharset = StandardCharsets.UTF_8;
                }

                byte[] bytes = new byte[(int) file.length()];
                try (InputStream fis = new java.io.FileInputStream(file)) {
                    int offset = 0;
                    while (offset < bytes.length) {
                        int count = fis.read(bytes, offset, bytes.length - offset);
                        if (count < 0) break;
                        offset += count;
                    }
                }

                String fullText = new String(bytes, mDefaultCharset);
                if (fullText.contains("\r\n")) mLineSeparator = "\r\n";
                else if (fullText.contains("\r")) mLineSeparator = "\r";
                else mLineSeparator = "\n";
                mFileModifiedManually = false;
                loadedBuffer = new GapBuffer(fullText);
                success = true;
            } catch (Exception error) {
                Log.e(TAG, "Read failed", error);
            }
            final GapBuffer resultBuffer = loadedBuffer;
            final boolean result = success;
            mHandler.post(() -> {
                if (result && resultBuffer != null) editView.setBuffer(resultBuffer);
                editView.setEditedMode(true);
                mHandler.sendEmptyMessage(0);
                mIndeterminateBar.setVisibility(View.GONE);
                if (!result) Toast.makeText(MainActivity.this, "Open failed", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void writeFileAsync(final String path) {
        ioExecutor.execute(() -> {
            boolean success = false;
            try {
                File file = new File(path);
                if (sourceUri == null && file.isFile() && editor_pref.getBoolean("generate_backup_file", false)) {
                    File backup = new File(file.getParentFile(), file.getName() + ".bak");
                    try (InputStream original = new java.io.FileInputStream(file);
                         OutputStream copy = new java.io.FileOutputStream(backup)) {
                        byte[] buffer = new byte[1024 * 256];
                        int read;
                        while ((read = original.read(buffer)) >= 0) if (read > 0) copy.write(buffer, 0, read);
                    }
                }
                String content = editView.getBuffer().toString();
                if (!"\n".equals(mLineSeparator)) content = content.replace("\n", mLineSeparator);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                     java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, mDefaultCharset)) {
                    osw.write(content);
                    osw.flush();
                }
                if (sourceUri != null) {
                    try (InputStream in = new java.io.FileInputStream(file);
                         OutputStream out = getContentResolver().openOutputStream(sourceUri, "wt")) {
                        if (out == null) throw new IOException("Cannot write document");
                        byte[] buffer = new byte[1024 * 256];
                        int read;
                        while ((read = in.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
                        out.flush();
                    }
                }
                mFileModifiedManually = false;
                success = true;
            } catch (Exception error) {
                Log.e(TAG, "Write failed", error);
            }
            final boolean result = success;
            mHandler.post(() -> Toast.makeText(
                    getApplicationContext(),
                    result ? "saved success!" : "save failed!",
                    result ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
            ).show());
        });
    }

    private File resolveSharedStorageRoot() {
        File current = getExternalFilesDir(null);
        if (current == null) return new File("/storage/emulated/0");
        for (int i = 0; i < 4 && current.getParentFile() != null; i++) current = current.getParentFile();
        return current;
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }


    private int themeColor(int attr, int fallback) {
        TypedValue out = new TypedValue();
        if (getTheme().resolveAttribute(attr, out, true)) {
            if (out.resourceId != 0) return getColor(out.resourceId);
            return out.data;
        }
        return fallback;
    }

    private void searchPanel() {
        edittext_find.requestFocus();

        search_pad.setVisibility(View.VISIBLE);
        if (!editView.getEditedMode()) {
            replace_btn.setEnabled(false);
            replace_btn.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.GRAY));
        } else {
            replace_btn.setTextColor(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
            replace_btn.setEnabled(true);
        }
        replace_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replace_all_btn.setTextColor(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
                replace_all_btn.setEnabled(true);
                if (linear_rep.getVisibility() == View.VISIBLE)
                    editView.replaceFirst(edittext_replace.getText().toString());
                else
                    linear_rep.setVisibility(View.VISIBLE);
            }
        });
        replace_all_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editView.replaceAll(edittext_replace.getText().toString());
            }
        });
        next_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editView.next();
            }
        });
        previous_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editView.previous();
            }
        });
        edittext_find.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    // Only regex implented here
                    editView.find(s.toString());
                } catch (Exception e) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        item_menu.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                PopupMenu popup = new PopupMenu(MainActivity.this, item_menu);
                popup.inflate(R.menu.menu_search_options);
                popup.getMenu().findItem(R.id.search_option_regex).setChecked(true);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();
                        if (id == R.id.search_option_regex) {
                            // to do
                        } else if (id == R.id.search_option_whole_word) {
                            // to do
                        } else if (id == R.id.search_option_match_case) {
                            // to do
                        } else if (id == R.id.close_search_options) {
                            search_pad.setVisibility(View.GONE);
                            edittext_find.setText("");
                            editView.find("");
                        }
                        return true;
                    }
                });
                popup.show();
            }
        });
    }

    public void addFunctionBar(LinearLayout container, final EditView editView) {
        Toast.makeText(getApplication(), "A basic implantation has done here.. Currently i am studing about it to fix the known issues", Toast.LENGTH_SHORT).show();
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.removeAllViews();

        for (String symbol : SYMBOLS) {

            final TextView tv = new TextView(container.getContext());
            tv.setText(symbol);
            tv.setTag(symbol);
            tv.setBackground(getSelectableBackground());
            tv.setTextSize(18f);
            tv.setTextColor(themeColor(android.R.attr.textColorPrimary, Color.WHITE));
            tv.setPadding(30, 20, 30, 20);
            tv.setGravity(Gravity.CENTER);

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (edittext_find.hasFocus()) {
                        String str = new String(edittext_find.getText().toString());
                        edittext_find.setText(str.concat(tv.getText().toString()));
                    } else {
                        editView.insertText(tv.getText().toString());
                    }
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
            );

            container.addView(tv, lp);
        }
    }

    private Drawable getSelectableBackground() {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

        return getResources().getDrawable(outValue.resourceId, getTheme());
    }
	
	private List<SyntaxItem> loadSyntaxList() {
		try {
			InputStream is = getAssets().open("availableSyntax.json");
			int size = is.available();
			byte[] buffer = new byte[size];
			is.read(buffer);
			is.close();
			String json = new String(buffer, "UTF-8");

			JSONArray arr = new JSONArray(json);
			List<SyntaxItem> list = new ArrayList<>();

			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.getJSONObject(i);
				SyntaxItem item = new SyntaxItem();
				item.Syntax = o.getString("Syntax");
				item.Path = o.getString("Path");
				list.add(item);
			}
			return list;

		} catch (Exception e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}
	
	public class SyntaxItem {
		public String Syntax;
		public String Path;
	}
	

    public static String readFile(String path) {
        StringBuilder sb = new StringBuilder();
        FileReader fr = null;
        try {
            fr = new FileReader(new File(path));

            char[] buff = new char[1024];
            int length = 0;

            while ((length = fr.read(buff)) > 0) {
                sb.append(new String(buff, 0, length));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }

}
