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
import android.view.ViewGroup;
import android.widget.Button;
import androidx.activity.OnBackPressedCallback;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextThemeWrapper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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

    private static final int OPEN_DOCUMENT = 8801, SAVE_DOCUMENT = 8802, PICK_COLOR = 8803;
    private static final int MAX_OPEN_FILES = 12;
    private static final long MAX_EDITOR_BYTES = 16L * 1024 * 1024;
    private EditorDrawerLayout fileDrawer;
    private TextView navigationDocumentTitle;
    private ImageButton navigationSaveButton;
    private final List<EditorTab> tabs = new ArrayList<>();
    private EditorTab activeTab;
    private EditorTab savingAsTab;
    private Runnable savingAsSuccess;
    private boolean restoringSession;
    private final Runnable checkpoint = this::persistEditorSession;
    private volatile boolean discardSession;

    private static final class EditorTab {
        final String id = UUID.randomUUID().toString();
        File file;
        Uri uri;
        String title = "Unbenannt", savedText = "";
        Charset charset = StandardCharsets.UTF_8, savedCharset = StandardCharsets.UTF_8;
        String eol = "\n", savedEol = "\n";
        EditView view;
        boolean loading, saving;
    }

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
        setupFileNavigation();
        initializeLogic();
    }

    private void configureWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        final View root = findViewById(R.id.rootLayout);
        final View bottom = findViewById(R.id.linear_bottom_layout);
        final boolean dark = isHostDarkTheme();
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
        setTheme(isHostDarkTheme() ? R.style.MTApktoolEditorTheme_Dark : R.style.MTApktoolEditorTheme_Light);
    }

    private boolean isHostDarkTheme() {
        String mode = getSharedPreferences("mtapktool_theme_bridge", MODE_PRIVATE)
                .getString("mode", "SYSTEM");
        if ("DARK".equals(mode)) return true;
        if ("LIGHT".equals(mode)) return false;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
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

        EditorTab draft = new EditorTab(); draft.view = editView; tabs.add(draft); activeTab = draft;
        bindDocument(draft);
        boolean restored = restoreEditorSession();
        Intent launchIntent = getIntent();
        String directPath = launchIntent != null ? launchIntent.getStringExtra("path") : null;
        String directUri = launchIntent != null ? launchIntent.getStringExtra("uri") : null;
        String title = launchIntent != null ? launchIntent.getStringExtra("name") : null;
        if (directUri != null && !directUri.isEmpty()) openDocument(null, Uri.parse(directUri), title);
        else if (directPath != null && !directPath.isEmpty()) openDocument(new File(directPath), null, title);
        else if (!restored && mSharedPreference.contains("path")) {
            File previous = new File(mSharedPreference.getString("path", ""));
            if (previous.isFile() && !previous.getPath().startsWith(getCacheDir().getPath())) openDocument(previous, null, previous.getName());
        }
        rebuildFileDrawer();
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
        if (activeTab != null && !activeTab.loading && !activeTab.saving && isDirty(activeTab)) {
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
        menu.add(0, 9901, 100, "Dokument öffnen");
        menu.add(0, 9902, 101, "Speichern unter");
        menu.add(0, 9903, 102, "Tab schließen");
        menu.add(0, 9904, 103, "Color Picker");
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { rebuildFileDrawer(); fileDrawer.open(); return true; }
        if (id == 9901) { Intent chooser = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(chooser, OPEN_DOCUMENT); return true; }
        if (id == 9902) { saveAs(activeTab, null); return true; }
        if (id == 9903) { if (activeTab != null) closeTab(activeTab); return true; }
        if (id == 9904) {
            Intent color = new Intent().setClassName(getPackageName(), "io.github.lootdev78.mtapktool.feature.tools.ToolsActivity").putExtra("tool", "color").putExtra("pick", true);
            startActivityForResult(color, PICK_COLOR); return true;
        }
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
            saveTab(activeTab, null);
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
                    openDocument(new File(pathname), null, null);
                }
            }
        });
        builder.setCancelable(true).show();
    }

    private void readFileAsync(String path) { openDocument(new File(path), null, null); }
    private void writeFileAsync(String path) { saveTab(activeTab, null); }

    private void setupFileNavigation() {
        View content = findViewById(R.id.rootLayout);
        ViewGroup parent = (ViewGroup) content.getParent();
        int index = parent.indexOfChild(content);
        parent.removeView(content);
        fileDrawer = new EditorDrawerLayout(this, content, hostColor("surface", android.R.attr.colorBackground, Color.DKGRAY));
        parent.addView(fileDrawer, index, new ViewGroup.LayoutParams(-1, -1));
        fileDrawer.setOnOpen(this::rebuildFileDrawer);
        ViewCompat.setOnApplyWindowInsetsListener(fileDrawer, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int spacing = fileDrawer.dp(8);
            fileDrawer.rows.setPadding(spacing + safe.left, spacing + safe.top, spacing + safe.right, spacing + safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(fileDrawer);
        // Always-visible entry, including devices/themes with no native ActionBar.
        if (content instanceof android.widget.RelativeLayout) {
            android.widget.RelativeLayout root = (android.widget.RelativeLayout) content;
            LinearLayout navigation = new LinearLayout(this);
            navigation.setId(View.generateViewId()); navigation.setGravity(Gravity.CENTER_VERTICAL);
            navigation.setBackgroundColor(hostColor("navigation", android.R.attr.colorBackground, Color.DKGRAY));
            android.widget.ImageButton button = new android.widget.ImageButton(this);
            button.setImageResource(R.drawable.mt_editor_ic_navigation);
            button.setImageTintList(android.content.res.ColorStateList.valueOf(hostColor("on_surface", android.R.attr.textColorPrimary, Color.WHITE)));
            button.setBackground(getSelectableBackground());
            button.setContentDescription("Dateinavigation: offene und zuletzt geöffnete Dateien");
            button.setOnClickListener(v -> fileDrawer.open());
            navigation.addView(button, new LinearLayout.LayoutParams(fileDrawer.dp(48), fileDrawer.dp(44)));
            navigationDocumentTitle = new TextView(this);
            navigationDocumentTitle.setText("Dateien"); navigationDocumentTitle.setSingleLine(true);
            navigationDocumentTitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            navigationDocumentTitle.setTextColor(hostColor("on_surface", android.R.attr.textColorPrimary, Color.WHITE));
            navigationDocumentTitle.setTextSize(14);
            navigation.addView(navigationDocumentTitle, new LinearLayout.LayoutParams(0, -2, 1));
            TextView open = new TextView(this); open.setText("+"); open.setTextSize(24); open.setGravity(Gravity.CENTER);
            open.setTextColor(hostColor("primary", android.R.attr.colorAccent, Color.CYAN));
            open.setContentDescription("Dokument öffnen"); open.setBackground(getSelectableBackground());
            open.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), OPEN_DOCUMENT));
            navigation.addView(open, new LinearLayout.LayoutParams(fileDrawer.dp(48), fileDrawer.dp(44)));
            android.widget.ImageButton save = new android.widget.ImageButton(this);
            navigationSaveButton = save;
            save.setImageResource(R.drawable.mt_editor_ic_save);
            save.setImageTintList(android.content.res.ColorStateList.valueOf(hostColor("on_surface", android.R.attr.textColorPrimary, Color.WHITE)));
            save.setContentDescription("Datei speichern"); save.setBackground(getSelectableBackground());
            save.setOnClickListener(v -> saveTab(activeTab, null));
            navigation.addView(save, new LinearLayout.LayoutParams(fileDrawer.dp(44), fileDrawer.dp(44)));
            TextView more = new TextView(this); more.setText("\u22ee"); more.setTextSize(26); more.setGravity(Gravity.CENTER);
            more.setTextColor(hostColor("on_surface", android.R.attr.textColorPrimary, Color.WHITE));
            more.setContentDescription("Editor-Menü"); more.setBackground(getSelectableBackground());
            more.setOnClickListener(v -> {
                PopupMenu popup = themedPopup(more);
                onCreateOptionsMenu(popup.getMenu()); onPrepareOptionsMenu(popup.getMenu());
                stylePopupMenu(popup.getMenu());
                popup.setOnMenuItemClickListener(this::onOptionsItemSelected); popup.show();
            });
            navigation.addView(more, new LinearLayout.LayoutParams(fileDrawer.dp(44), fileDrawer.dp(44)));
            android.widget.RelativeLayout.LayoutParams nav = new android.widget.RelativeLayout.LayoutParams(-1, fileDrawer.dp(44));
            nav.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP); root.addView(navigation, nav);
            View editor = root.findViewById(R.id.editorContainer);
            android.widget.RelativeLayout.LayoutParams editorParams = (android.widget.RelativeLayout.LayoutParams) editor.getLayoutParams();
            editorParams.addRule(android.widget.RelativeLayout.BELOW, navigation.getId()); editor.setLayoutParams(editorParams);
            View progress = root.findViewById(R.id.indeterminateBar);
            android.widget.RelativeLayout.LayoutParams progressParams = (android.widget.RelativeLayout.LayoutParams) progress.getLayoutParams();
            progressParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            progressParams.addRule(android.widget.RelativeLayout.BELOW, navigation.getId()); progress.setLayoutParams(progressParams);
        }
        content.setBackgroundColor(hostColor("surface", android.R.attr.colorBackground, Color.DKGRAY));
        findViewById(R.id.linear_bottom_layout).setBackgroundColor(hostColor("navigation", android.R.attr.colorBackground, Color.DKGRAY));
        search_pad.setBackgroundColor(hostColor("navigation", android.R.attr.colorBackground, Color.DKGRAY));
        getWindow().setStatusBarColor(hostColor("surface", android.R.attr.colorBackground, Color.DKGRAY));
        getWindow().setNavigationBarColor(hostColor("navigation", android.R.attr.colorBackground, Color.DKGRAY));
        if (getActionBar() != null && navigationDocumentTitle != null) getActionBar().hide();
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeAsUpIndicator(R.drawable.mt_editor_ic_navigation);
            getActionBar().setHomeActionContentDescription("Dateinavigation");
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (fileDrawer.isOpen()) { fileDrawer.close(); return; }
                if (tabs.stream().anyMatch(t -> t.loading || t.saving)) { Toast.makeText(MainActivity.this, "Dateioperation läuft", Toast.LENGTH_SHORT).show(); return; }
                if (tabs.stream().noneMatch(MainActivity.this::isDirty)) { persistEditorSession(); finish(); return; }
                new AlertDialog.Builder(MainActivity.this).setTitle("Ungespeicherte Dateien")
                    .setMessage("Änderungen vor dem Schließen speichern?")
                    .setPositiveButton("Speichern", (d, w) -> saveDirtyTabs(0))
                    .setNegativeButton("Verwerfen", (d, w) -> { discardSession = true; mHandler.removeCallbacks(checkpoint); ioExecutor.execute(() -> mSharedPreference.edit().remove("navigation_session").apply()); finish(); })
                    .setNeutralButton("Abbrechen", null).show();
            }
        });
    }
    private EditView newEditor() {
        EditView view = new EditView(this);
        view.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        view.setWordWrap(editor_pref.getBoolean("word_wrap", false));
        view.setAutoCompleteEnabled(editor_pref.getBoolean("auto_complete", true));
        view.setShowLineNumbers(editor_pref.getBoolean("show_line_numbers", true));
        view.setStickyLineNumbers(editor_pref.getBoolean("sticky_line_numbers", true));
        view.setShowIndentGuides(editor_pref.getBoolean("show_indent_guides", true));
        view.setShowWrapArrows(editor_pref.getBoolean("show_wrap_arrows", true));
        view.setAutoIndentEnabled(editor_pref.getBoolean("auto_indent", true));
        view.setTypeface(Typeface.DEFAULT);
        int syntax = editor_pref.getInt("syntax_position", 0);
        if (syntax > 0) { List<SyntaxItem> available = loadSyntaxList(); if (syntax <= available.size()) view.setSyntaxLanguageFileName(available.get(syntax - 1).Path); }
        int menu = editor_pref.getInt("menu_style", 0);
        view.setMenuStyle(menu == 1 ? ClipboardPanel.MenuDisplayMode.TEXT_ONLY : menu == 2 ? ClipboardPanel.MenuDisplayMode.ICON_ONLY : ClipboardPanel.MenuDisplayMode.ICON_AND_TEXT);
        return view;
    }
    private void bindDocument(EditorTab tab) {
        tab.view.setOnTextChangedListener(() -> {
            if (!tab.loading) {
                mHandler.sendEmptyMessage(0);
                mHandler.removeCallbacks(checkpoint); mHandler.postDelayed(checkpoint, 700);
                if (tab == activeTab) updateDocumentTitle();
            }
            tab.view.postInvalidate();
        });
        tab.view.setOnSelectionChangeListener((start, end) -> mHandler.sendEmptyMessage(0));
    }
    private void captureDocumentSettings() {
        if (activeTab == null) return;
        activeTab.charset = mDefaultCharset; activeTab.eol = mLineSeparator;
    }
    private boolean isDirty(EditorTab tab) {
        if (tab == null || tab.loading) return false;
        if (tab == activeTab) captureDocumentSettings();
        return !tab.savedText.equals(tab.view.getBuffer().toString()) || !tab.charset.equals(tab.savedCharset) || !tab.eol.equals(tab.savedEol);
    }
    private void updateDocumentTitle() {
        if (activeTab != null) setTitle(activeTab.title + (isDirty(activeTab) ? " *" : ""));
        if (activeTab != null && navigationDocumentTitle != null) navigationDocumentTitle.setText(activeTab.title + (isDirty(activeTab) ? " *" : ""));
        if (navigationSaveButton != null) {
            boolean canSave = activeTab != null && !activeTab.loading && !activeTab.saving && isDirty(activeTab);
            navigationSaveButton.setEnabled(canSave);
            navigationSaveButton.setAlpha(canSave ? 1f : .5f);
        }
    }
    private void selectTab(EditorTab tab) {
        captureDocumentSettings(); activeTab = tab; editView = tab.view;
        editorContainer.removeAllViews(); editorContainer.addView(editView);
        addFunctionBar(functionBar, editView);
        sourceUri = tab.uri; sourceDisplayName = tab.title;
        mDefaultCharset = tab.charset; mLineSeparator = tab.eol; mFileModifiedManually = false;
        if (tab.file != null) mSharedPreference.edit().putString("path", tab.file.getAbsolutePath()).apply();
        mIndeterminateBar.setVisibility(tab.loading ? View.VISIBLE : View.GONE);
        search_pad.setVisibility(View.GONE); updateDocumentTitle(); invalidateOptionsMenu(); rebuildFileDrawer();
    }
    private String documentKey(EditorTab tab) { return tab.uri != null ? tab.uri.toString() : tab.file != null ? tab.file.getAbsolutePath() : ""; }
    private void openDocument(File file, Uri uri, String title) {
        try { if (file != null) file = file.getCanonicalFile(); } catch (IOException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); return; }
        String key = uri != null ? uri.toString() : file != null ? file.getAbsolutePath() : "";
        for (EditorTab tab : tabs) if (key.equals(documentKey(tab)) && !key.isEmpty()) { selectTab(tab); fileDrawer.close(); return; }
        if (tabs.size() >= MAX_OPEN_FILES) { Toast.makeText(this, "Maximal 12 Dateien; einen Tab schließen", Toast.LENGTH_LONG).show(); return; }
        if (activeTab != null && activeTab.file == null && activeTab.uri == null && !isDirty(activeTab)) { tabs.remove(activeTab); }
        EditorTab tab = new EditorTab(); tab.uri = uri; tab.file = file;
        tab.title = title != null && !title.isEmpty() ? title : file != null ? file.getName() : "Dokument";
        tab.view = newEditor(); tab.loading = true; bindDocument(tab); tabs.add(tab); selectTab(tab); fileDrawer.close();
        final File inputFile = file;
        final String taskId = EditorTaskBridge.begin("Textdatei öffnen", tab.title);
        ioExecutor.execute(() -> {
            try {
                File local = inputFile;
                if (uri != null) {
                    local = new File(getCacheDir(), "mh-document-" + tab.id + ".txt");
                    try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(local)) {
                        if (in == null) throw new IOException("Dokument nicht lesbar");
                        byte[] buffer = new byte[64 * 1024]; long total = 0;
                        for (int count; (count = in.read(buffer)) >= 0;) { total += count; if (total > MAX_EDITOR_BYTES) throw new IOException("Datei größer als 16 MiB"); if (count > 0) out.write(buffer, 0, count); }
                    }
                }
                if (local == null || !local.isFile() || local.length() > MAX_EDITOR_BYTES) throw new IOException("Datei fehlt oder ist größer als 16 MiB");
                String detected = UniversalDetector.detectCharset(local);
                Charset charset = detected == null ? StandardCharsets.UTF_8 : Charset.forName(detected);
                String fullText = new String(Files.readAllBytes(local.toPath()), charset);
                String eol = fullText.contains("\r\n") ? "\r\n" : fullText.contains("\r") ? "\r" : "\n";
                String normalized = fullText.replace("\r\n", "\n").replace('\r', '\n');
                File loadedFile = local;
                EditorTaskBridge.finish(taskId, true, "Datei geladen: " + tab.title);
                mHandler.post(() -> {
                    if (!tabs.contains(tab)) return;
                    tab.file = loadedFile; tab.charset = charset; tab.savedCharset = charset; tab.eol = eol; tab.savedEol = eol;
                    tab.view.setBuffer(new GapBuffer(normalized)); tab.savedText = normalized; tab.loading = false;
                    tab.view.setEditedMode(true);
                    rememberRecent(tab);
                    if (activeTab == tab) selectTab(tab);
                    persistEditorSession();
                });
            } catch (Exception error) {
                EditorTaskBridge.finish(taskId, false, error.getMessage());
                mHandler.post(() -> { tab.loading = false; tabs.remove(tab); if (activeTab == tab) selectOrCreateLastTab(); Toast.makeText(this, "Öffnen fehlgeschlagen: " + error.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
    }
    private void saveTab(EditorTab tab, Runnable success) {
        if (tab == null || tab.loading || tab.saving) return;
        if (tab.file == null && tab.uri == null) { saveAs(tab, success); return; }
        if (tab == activeTab) captureDocumentSettings();
        final String snapshot = tab.view.getBuffer().toString();
        final Charset charset = tab.charset; final String eol = tab.eol;
        final Uri destinationUri = tab.uri;
        final File destination = tab.file;
        final String taskId = EditorTaskBridge.begin("Textdatei speichern", tab.title);
        tab.saving = true; updateDocumentTitle(); invalidateOptionsMenu();
        ioExecutor.execute(() -> {
            try {
                if (destination == null) throw new IOException("Speicherort fehlt");
                File staged = File.createTempFile(".mh-save-", ".tmp", destination.getParentFile());
                try {
                    try (OutputStream out = new FileOutputStream(staged)) { out.write(snapshot.replace("\n", eol).getBytes(charset)); }
                    if (destinationUri == null && destination.isFile()) android.system.Os.chmod(staged.getAbsolutePath(), android.system.Os.stat(destination.getAbsolutePath()).st_mode & 07777);
                    if (destinationUri == null && destination.isFile() && editor_pref.getBoolean("generate_backup_file", false))
                        Files.copy(destination.toPath(), new File(destination.getParentFile(), destination.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (destinationUri != null) {
                        try (InputStream in = new java.io.FileInputStream(staged); OutputStream out = getContentResolver().openOutputStream(destinationUri, "wt")) {
                            if (out == null) throw new IOException("Dokument nicht beschreibbar");
                            byte[] buffer = new byte[64 * 1024]; for (int n; (n = in.read(buffer)) >= 0;) if (n > 0) out.write(buffer, 0, n);
                        }
                    }
                    Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } finally { staged.delete(); }
                EditorTaskBridge.finish(taskId, true, "Datei gespeichert: " + tab.title);
                mHandler.post(() -> {
                    tab.saving = false; tab.savedText = snapshot; tab.savedCharset = charset; tab.savedEol = eol;
                    mFileModifiedManually = false; updateDocumentTitle(); rebuildFileDrawer(); invalidateOptionsMenu(); rememberRecent(tab); persistEditorSession();
                    Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show(); if (success != null) success.run();
                });
            } catch (Exception error) { EditorTaskBridge.finish(taskId, false, error.getMessage()); mHandler.post(() -> { tab.saving = false; updateDocumentTitle(); invalidateOptionsMenu(); Toast.makeText(this, "Speichern fehlgeschlagen: " + error.getMessage(), Toast.LENGTH_LONG).show(); }); }
        });
    }
    private void saveAs(EditorTab tab, Runnable success) {
        if (tab == null || tab.loading || tab.saving) return;
        savingAsTab = tab; savingAsSuccess = success;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, tab.title.equals("Unbenannt") ? "document.txt" : tab.title), SAVE_DOCUMENT);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) { if (request == SAVE_DOCUMENT) { savingAsTab = null; savingAsSuccess = null; } return; }
        if (request == OPEN_DOCUMENT && data.getData() != null) {
            Uri uri = data.getData(); try { getContentResolver().takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch (Exception ignored) { }
            String title = "Dokument";
            try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (cursor != null && cursor.moveToFirst()) title = cursor.getString(0); }
            openDocument(null, uri, title);
        } else if (request == SAVE_DOCUMENT && savingAsTab != null && data.getData() != null) {
            EditorTab tab = savingAsTab; Runnable success = savingAsSuccess; savingAsTab = null; savingAsSuccess = null;
            tab.uri = data.getData(); tab.file = new File(getCacheDir(), "mh-document-" + tab.id + ".txt");
            try { getContentResolver().takePersistableUriPermission(tab.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) { }
            saveTab(tab, success);
        } else if (request == PICK_COLOR) { String color = data.getStringExtra("color"); if (color != null) editView.insertText(color); }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        String path = intent.getStringExtra("path"), uri = intent.getStringExtra("uri");
        if (uri != null) openDocument(null, Uri.parse(uri), intent.getStringExtra("name")); else if (path != null) openDocument(new File(path), null, intent.getStringExtra("name"));
    }
    private void closeTab(EditorTab tab) {
        if (tab.loading || tab.saving) return;
        Runnable close = () -> { tabs.remove(tab); if (activeTab == tab) selectOrCreateLastTab(); rebuildFileDrawer(); persistEditorSession(); };
        if (!isDirty(tab)) { close.run(); return; }
        new AlertDialog.Builder(this).setTitle(tab.title).setMessage("Änderungen speichern?")
            .setPositiveButton("Speichern", (d, w) -> saveTab(tab, close)).setNegativeButton("Verwerfen", (d, w) -> close.run()).setNeutralButton("Abbrechen", null).show();
    }
    private void selectOrCreateLastTab() {
        if (tabs.isEmpty()) { EditorTab draft = new EditorTab(); draft.view = newEditor(); bindDocument(draft); tabs.add(draft); }
        selectTab(tabs.get(tabs.size() - 1));
    }
    private void saveDirtyTabs(int index) {
        if (index >= tabs.size()) { persistEditorSession(); finish(); return; }
        EditorTab tab = tabs.get(index);
        if (isDirty(tab)) saveTab(tab, () -> saveDirtyTabs(index + 1)); else saveDirtyTabs(index + 1);
    }
    private void rememberRecent(EditorTab tab) {
        String key = documentKey(tab); if (key.isEmpty()) return;
        JSONArray previous = recentFiles(); JSONArray next = new JSONArray();
        next.put(json("path", key, "name", tab.title));
        for (int i = 0; i < previous.length() && next.length() < 40; i++) { JSONObject item = previous.optJSONObject(i); if (item != null && !key.equals(item.optString("path"))) next.put(item); }
        mSharedPreference.edit().putString("navigation_recent", next.toString()).apply();
    }
    private JSONArray recentFiles() { try { return new JSONArray(mSharedPreference.getString("navigation_recent", "[]")); } catch (Exception ignored) { return new JSONArray(); } }
    private TextView navigationLabel(String text, boolean heading) {
        TextView row = new TextView(this); row.setText(text); row.setTextColor(hostColor(heading ? "primary" : "on_surface", heading ? android.R.attr.colorAccent : android.R.attr.textColorPrimary, Color.WHITE));
        row.setTextSize(heading ? 14 : 13); row.setPadding(fileDrawer.dp(12), fileDrawer.dp(heading ? 18 : 10), fileDrawer.dp(8), fileDrawer.dp(10));
        row.setMinHeight(fileDrawer.dp(44)); row.setGravity(Gravity.CENTER_VERTICAL);
        if (heading) row.setTypeface(null, Typeface.BOLD); else row.setBackground(getSelectableBackground()); return row;
    }
    private void rebuildFileDrawer() {
        if (fileDrawer == null) return;
        fileDrawer.rows.removeAllViews();
        fileDrawer.rows.addView(navigationLabel("Dateien", true));
        TextView open = navigationLabel("+ Datei öffnen", false); open.setOnClickListener(v -> { fileDrawer.close(); startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), OPEN_DOCUMENT); }); fileDrawer.rows.addView(open);
        fileDrawer.rows.addView(navigationLabel("Geöffnete Dateien", true));
        for (EditorTab tab : new ArrayList<>(tabs)) {
            LinearLayout row = new LinearLayout(this);
            if (tab == activeTab) row.setBackgroundColor(hostColor("container", android.R.attr.colorControlHighlight, Color.DKGRAY));
            TextView label = navigationLabel((tab == activeTab ? "› " : "") + (isDirty(tab) ? "● " : "") + tab.title, false);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); label.setOnClickListener(v -> { selectTab(tab); fileDrawer.close(); });
            TextView close = navigationLabel("×", false); close.setContentDescription("Tab schließen"); close.setOnClickListener(v -> closeTab(tab)); row.addView(close); fileDrawer.rows.addView(row);
        }
        fileDrawer.rows.addView(navigationLabel("Zuletzt geöffnet", true));
        JSONArray recent = recentFiles();
        for (int i = 0; i < recent.length(); i++) {
            JSONObject item = recent.optJSONObject(i); if (item == null) continue;
            String key = item.optString("path"), title = item.optString("name");
            TextView row = navigationLabel(title + "\n" + key, false); row.setMaxLines(2); row.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); row.setOnClickListener(v -> { fileDrawer.close(); if (key.startsWith("content://")) openDocument(null, Uri.parse(key), title); else openDocument(new File(key), null, title); });
            row.setOnLongClickListener(v -> { JSONArray next = new JSONArray(); for (int n = 0; n < recent.length(); n++) { JSONObject value = recent.optJSONObject(n); if (value != null && !key.equals(value.optString("path"))) next.put(value); } mSharedPreference.edit().putString("navigation_recent", next.toString()).apply(); rebuildFileDrawer(); return true; });
            fileDrawer.rows.addView(row);
        }
        if (activeTab != null && activeTab.uri == null && activeTab.file != null) {
            File directory = activeTab.file.getParentFile();
            TextView header = navigationLabel("Aktueller Ordner", true); fileDrawer.rows.addView(header);
            ioExecutor.execute(() -> {
                File[] children = directory == null ? null : directory.listFiles(f -> f.isFile() && f.length() <= MAX_EDITOR_BYTES && isTextName(f.getName()));
                if (children == null) return; Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                mHandler.post(() -> { if (activeTab == null || activeTab.file == null || !directory.equals(activeTab.file.getParentFile()) || header.getParent() == null) return;
                    for (int i = 0; i < Math.min(children.length, 100); i++) { File child = children[i]; TextView row = navigationLabel(child.getName(), false); row.setOnClickListener(v -> { fileDrawer.close(); openDocument(child, null, child.getName()); }); fileDrawer.rows.addView(row); }
                });
            });
        }
    }
    private void persistEditorSession() {
        if (discardSession || restoringSession || tabs.isEmpty()) return;
        captureDocumentSettings();
        JSONArray metadata = new JSONArray(); List<String[]> buffers = new ArrayList<>();
        File sessionDirectory = new File(getFilesDir(), "mh-editor-session");
        for (EditorTab tab : tabs) {
            if (tab.loading) continue;
            JSONObject item = json("id", tab.id, "file", tab.file == null ? "" : tab.file.getAbsolutePath(), "uri", tab.uri == null ? "" : tab.uri.toString(),
                "title", tab.title, "charset", tab.charset.name(), "savedCharset", tab.savedCharset.name(), "eol", tab.eol, "savedEol", tab.savedEol,
                "cursor", tab.view.getSelectionStart(), "active", tab == activeTab);
            metadata.put(item); buffers.add(new String[]{tab.id, tab.view.getBuffer().toString(), tab.savedText});
        }
        ioExecutor.execute(() -> {
            try {
                if (discardSession) return;
                if (!sessionDirectory.exists() && !sessionDirectory.mkdirs()) return;
                Set<String> live = new HashSet<>();
                for (String[] buffer : buffers) { live.add(buffer[0] + ".draft"); live.add(buffer[0] + ".saved");
                    for (int n = 1; n <= 2; n++) { File output = new File(sessionDirectory, buffer[0] + (n == 1 ? ".draft" : ".saved")); File temp = new File(sessionDirectory, output.getName() + ".tmp"); Files.write(temp.toPath(), buffer[n].getBytes(StandardCharsets.UTF_8)); Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                }
                if (!discardSession) mSharedPreference.edit().putString("navigation_session", metadata.toString()).apply();
                File[] old = sessionDirectory.listFiles(); if (old != null) for (File file : old) if (!live.contains(file.getName())) file.delete();
            } catch (Exception e) { Log.e(TAG, "Editor checkpoint failed", e); }
        });
    }
    private static JSONObject json(Object... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); }
        catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        return result;
    }
    private static boolean isTextName(String name) {
        return name.matches("(?i).+\\.(txt|md|xml|json|smali|java|kt|kts|gradle|html|css|js|ts|tsx|jsx|yaml|yml|ini|conf|properties|sh|py|c|cpp|h|hpp|log|csv|svg)$") || name.equalsIgnoreCase("LICENSE") || name.equalsIgnoreCase("Makefile");
    }
    private boolean restoreEditorSession() {
        JSONArray metadata; try { metadata = new JSONArray(mSharedPreference.getString("navigation_session", "[]")); } catch (Exception e) { return false; }
        if (metadata.length() == 0) return false;
        restoringSession = true;
        ioExecutor.execute(() -> {
            List<String[]> restored = new ArrayList<>();
            File directory = new File(getFilesDir(), "mh-editor-session");
            for (int i = 0; i < Math.min(metadata.length(), MAX_OPEN_FILES); i++) try {
                JSONObject item = metadata.getJSONObject(i); String id = item.getString("id"); if (!id.matches("[0-9a-f-]{36}")) continue;
                File draft = new File(directory, id + ".draft"), saved = new File(directory, id + ".saved");
                if (!draft.isFile() || !saved.isFile() || draft.length() > MAX_EDITOR_BYTES || saved.length() > MAX_EDITOR_BYTES) continue;
                restored.add(new String[]{item.toString(), new String(Files.readAllBytes(draft.toPath()), StandardCharsets.UTF_8), new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8)});
            } catch (Exception e) { Log.w(TAG, "Cannot restore editor tab", e); }
            mHandler.post(() -> {
                for (String[] entry : restored) try {
                    JSONObject item = new JSONObject(entry[0]); String key = item.optString("uri").isEmpty() ? item.optString("file") : item.optString("uri");
                    if (!key.isEmpty() && tabs.stream().anyMatch(t -> key.equals(documentKey(t)))) continue;
                    if (tabs.size() >= MAX_OPEN_FILES) break;
                    EditorTab tab = new EditorTab(); tab.title = item.optString("title", "Unbenannt"); String path = item.optString("file"); tab.file = path.isEmpty() ? null : new File(path);
                    String uri = item.optString("uri"); tab.uri = uri.isEmpty() ? null : Uri.parse(uri);
                    if (tab.uri != null && tab.file == null) tab.file = new File(getCacheDir(), "mh-document-" + tab.id + ".txt");
                    tab.charset = Charset.forName(item.optString("charset", "UTF-8")); tab.savedCharset = Charset.forName(item.optString("savedCharset", "UTF-8")); tab.eol = item.optString("eol", "\n"); tab.savedEol = item.optString("savedEol", "\n");
                    tab.view = newEditor(); tab.view.setBuffer(new GapBuffer(entry[1])); tab.savedText = entry[2]; bindDocument(tab); tabs.add(tab); tab.view.setSelection(item.optInt("cursor"), item.optInt("cursor"));
                    if (item.optBoolean("active") && (activeTab == null || documentKey(activeTab).isEmpty() && !isDirty(activeTab))) selectTab(tab);
                } catch (Exception e) { Log.w(TAG, "Cannot restore tab", e); }
                if (tabs.size() > 1) tabs.removeIf(t -> t != activeTab && documentKey(t).isEmpty() && !isDirty(t));
                restoringSession = false; rebuildFileDrawer(); persistEditorSession();
            });
        });
        return true;
    }

    private File resolveSharedStorageRoot() {
        File current = getExternalFilesDir(null);
        if (current == null) return new File("/storage/emulated/0");
        for (int i = 0; i < 4 && current.getParentFile() != null; i++) current = current.getParentFile();
        return current;
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        ioExecutor.shutdown();
        super.onDestroy();
    }


    private PopupMenu themedPopup(View anchor) {
        ContextThemeWrapper popupContext = new ContextThemeWrapper(this,
                isHostDarkTheme() ? R.style.MTApktoolEditorPopup_Dark : R.style.MTApktoolEditorPopup_Light);
        return new PopupMenu(popupContext, anchor, Gravity.END);
    }

    private void stylePopupMenu(Menu menu) {
        int text = hostColor("on_surface", android.R.attr.textColorPrimary, isHostDarkTheme() ? Color.WHITE : Color.BLACK);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            int color = item.isEnabled() ? text : (text & 0x00FFFFFF) | 0x66000000;
            if (item.getTitle() != null) {
                SpannableString title = new SpannableString(item.getTitle().toString());
                title.setSpan(new ForegroundColorSpan(color), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                item.setTitle(title);
            }
            if (item.getIcon() != null) item.getIcon().mutate().setTint(color);
            if (item.hasSubMenu()) stylePopupMenu(item.getSubMenu());
        }
    }

    private int hostColor(String key, int attr, int fallback) {
        return getSharedPreferences("mtapktool_theme_bridge", MODE_PRIVATE).getInt(key, themeColor(attr, fallback));
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
                PopupMenu popup = themedPopup(item_menu);
                popup.inflate(R.menu.menu_search_options);
                stylePopupMenu(popup.getMenu());
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
        int accent = hostColor("primary", android.R.attr.colorAccent, Color.CYAN);
        return new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf((accent & 0x00ffffff) | 0x33000000),
            null, new android.graphics.drawable.ColorDrawable(Color.WHITE));
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
