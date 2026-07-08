package com.kinetic.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Main activity. Houses gallery, generate panel, remix panel,
 * image viewer, settings, and generation overlay.
 */
public class MainActivity extends Activity {
    // --- Constants ---
    private static final int PICK_IMAGE_REF = 101;
    private static final int PICK_IMAGE_REMIX = 102;
    private static final int PICK_IMAGE_AVATAR = 103;
    private static final int PERM_WRITE = 300;
    private static final int PERM_MEDIA = 301;

    // --- Default models (current AquaDevs API, old ones removed) ---
    private static final Prefs.ModelItem[] BUILT_IN_MODELS = {
        new Prefs.ModelItem("gptimage-2", "GPT Image 2", true, false),
        new Prefs.ModelItem("qwen-image", "Qwen Image", true, false),
        new Prefs.ModelItem("ideogram", "Ideogram V4", false, false),
        new Prefs.ModelItem("agnes-image", "Agnes Image 2.1 Flash", false, false),
    };

    private static final String[] BUILT_IN_ACTIONS = {
        "Sketch|Redraw as a detailed pencil sketch with fine graphite lines",
        "3D Render|Transform into a photorealistic 3D rendered scene with volumetric lighting",
        "Watercolor|Repaint in watercolor style with soft washes and visible brush strokes",
        "Pixel Art|Convert to retro pixel art with limited palette and visible pixels",
        "Anime|Redraw in anime art style with cel shading and vibrant colors",
        "Oil Paint|Repaint as classical oil painting with rich textures",
        "Cyberpunk|Transform into neon cyberpunk aesthetic with glowing outlines",
        "Stained Glass|Convert to stained glass art with bold leading lines and jewel tones",
    };

    private static final String[] RANDOM_PROMPTS = {
        "A majestic dragon perched atop a crystal mountain at sunset, scales reflecting prismatic light, fantasy concept art",
        "Futuristic cyberpunk city at night, neon holographic billboards, flying vehicles, rainy streets, cinematic wide shot",
        "A serene Japanese zen garden in autumn, golden ginkgo leaves falling, koi pond reflecting maple trees",
        "An astronaut discovering a bioluminescent alien garden on a distant planet, volumetric fog, sci-fi concept art",
        "A cozy enchanted cottage deep in a mossy forest, fireflies dancing, warm amber light from windows",
        "Underwater ancient temple covered in coral and sea life, god rays piercing turquoise water, photorealistic 8K",
        "A steampunk mechanical owl with intricate brass gears and copper feathers, perched on antique books",
        "A celestial goddess composed of stars and nebulae, flowing cosmic dress, digital painting",
        "A lone samurai in a vast field of red spider lilies, wind billowing through cloak, cinematic golden hour",
        "Miniature terrarium world on a scientist desk, tiny mountains and rivers inside glass dome, tilt-shift photography",
        "A massive whale swimming through clouds above a Victorian city, airships nearby, Studio Ghibli inspired",
        "Photorealistic chameleon on tropical branch, incredible skin detail, bokeh background, macro photography",
    };

    // --- State ---
    private Prefs prefs;
    private KineticDB db;
    private GalleryAdapter galleryAdapter;
    private List<KineticDB.ImageEntry> allImages = new ArrayList<>();
    private String currentFilter = "all";
    private String currentView = "gallery";
    private boolean sidebarOpen = false;
    private boolean panelOpen = false;
    private String panelType = ""; // "generate" or "remix"
    private String selectedModel = "gptimage-2";
    private String selectedRatio = "landscape";
    private int selectedCount = 1;
    private String selectedStyle = "";
    private String refImageBase64;
    private String remixRefImageBase64;
    private String remixSelectedRatio = "landscape";
    private int remixSelectedCount = 1;
    private boolean isGenerating = false;

    // --- Views ---
    private View sidebar, sidebarOverlay;
    private FrameLayout contentContainer, panelContainer, genOverlay;
    private TextView topbarTitle;
    private ImageView avatarImg;
    private TextView avatarLetter;
    private View avatarBg;

    // Sidebar nav
    private TextView navGenerate, navGallery, navRemix, navSettings;

    // Gallery
    private View galleryView;
    private GridView galleryGrid;
    private View emptyState;
    private TextView tabAll, tabRecent, tabFavorites, imageCount;
    private EditText searchInput;

    // Generate panel
    private View generatePanel;
    private EditText promptInput;
    private TextView charCount, modelSelector;
    private View enhanceBtn, generateBtn, shuffleBtn;
    private LinearLayout styleChipsContainer;
    private View refDropZone, refPlaceholder;
    private ImageView refPreview;

    // Remix panel
    private View remixPanel;
    private GridView remixPickGrid;
    private View remixUploadZone, remixSelectedWrap;
    private ImageView remixSelectedImg;
    private EditText remixPromptInput;
    private View remixBtn;
    private LinearLayout actionGridContainer;

    // Generation overlay
    private View genOrb;
    private TextView genPromptDisplay, genStatus, genCounter;
    private ProgressBar genProgressBar;

    // Toast
    private LinearLayout toastContainer;

    // =============================================
    // LIFECYCLE
    // =============================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        db = new KineticDB(this);

        requestStoragePermission();
        initViews();
        initSidebar();
        initGallery();
        initGeneratePanel();
        initRemixPanel();
        loadGalleryData();
        switchContent("gallery");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAvatar();
    }

    // =============================================
    // PERMISSIONS
    // =============================================

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERM_MEDIA);
            }
        } else if (Build.VERSION.SDK_INT <= 28) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, PERM_WRITE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED) {
            showToast(getString(R.string.storage_permission_needed), "warn");
        }
    }

    // =============================================
    // INIT VIEWS
    // =============================================

    private void initViews() {
        sidebar = findViewById(R.id.sidebar);
        sidebarOverlay = findViewById(R.id.sidebarOverlay);
        contentContainer = findViewById(R.id.contentContainer);
        panelContainer = findViewById(R.id.panelContainer);
        genOverlay = findViewById(R.id.genOverlay);
        topbarTitle = findViewById(R.id.topbarTitle);
        toastContainer = findViewById(R.id.toastContainer);

        // Avatar
        avatarBg = findViewById(R.id.avatarBg);
        avatarLetter = findViewById(R.id.avatarLetter);
        avatarImg = findViewById(R.id.avatarImg);
        updateAvatar();

        // Menu button
        findViewById(R.id.btnMenu).setOnClickListener(v -> toggleSidebar());
        sidebarOverlay.setOnClickListener(v -> closeSidebar());
        findViewById(R.id.btnAvatar).setOnClickListener(v -> showSettings());

        // Nav items
        navGenerate = findViewById(R.id.navGenerate);
        navGallery = findViewById(R.id.navGallery);
        navRemix = findViewById(R.id.navRemix);
        navSettings = findViewById(R.id.navSettings);

        // Generation overlay views
        genOrb = findViewById(R.id.genOrb);
        genPromptDisplay = findViewById(R.id.genPromptDisplay);
        genStatus = findViewById(R.id.genStatus);
        genCounter = findViewById(R.id.genCounter);
        genProgressBar = findViewById(R.id.genProgressBar);
    }

    private void updateAvatar() {
        String letter = prefs.getName();
        avatarLetter.setText(letter != null && !letter.isEmpty() ? letter.substring(0, 1).toUpperCase() : "K");

        String avatarData = prefs.getAvatar();
        if (avatarData != null && !avatarData.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(avatarData, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                avatarImg.setImageBitmap(bmp);
                avatarImg.setVisibility(View.VISIBLE);
                avatarLetter.setVisibility(View.GONE);
            } catch (Exception e) {
                avatarImg.setVisibility(View.GONE);
                avatarLetter.setVisibility(View.VISIBLE);
            }
        } else {
            avatarImg.setVisibility(View.GONE);
            avatarLetter.setVisibility(View.VISIBLE);
        }
    }

    // =============================================
    // SIDEBAR
    // =============================================

    private void initSidebar() {
        navGenerate.setOnClickListener(v -> { switchContent("generate"); closeSidebar(); });
        navGallery.setOnClickListener(v -> { switchContent("gallery"); closeSidebar(); });
        navRemix.setOnClickListener(v -> { switchContent("remix"); closeSidebar(); });
        navSettings.setOnClickListener(v -> { showSettings(); closeSidebar(); });
    }

    private void toggleSidebar() {
        if (sidebarOpen) closeSidebar();
        else openSidebar();
    }

    private void openSidebar() {
        sidebarOpen = true;
        sidebarOverlay.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(sidebar, "translationX", 0f).setDuration(300).start();
        ObjectAnimator.ofFloat(sidebarOverlay, "alpha", 1f).setDuration(300).start();
    }

    private void closeSidebar() {
        sidebarOpen = false;
        float sw = getResources().getDimension(R.dimen.sidebar_width);
        ObjectAnimator slideOut = ObjectAnimator.ofFloat(sidebar, "translationX", -sw);
        slideOut.setDuration(300).start();
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(sidebarOverlay, "alpha", 0f);
        fadeOut.setDuration(300);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                sidebarOverlay.setVisibility(View.GONE);
            }
        });
        fadeOut.start();
    }

    // =============================================
    // CONTENT SWITCHING
    // =============================================

    private void switchContent(String view) {
        currentView = view;
        contentContainer.removeAllViews();

        // Update nav highlighting
        navGenerate.setTextColor(getColor(R.color.text2));
        navGenerate.setBackgroundColor(Color.TRANSPARENT);
        navGallery.setTextColor(getColor(R.color.text2));
        navGallery.setBackgroundColor(Color.TRANSPARENT);
        navRemix.setTextColor(getColor(R.color.text2));
        navRemix.setBackgroundColor(Color.TRANSPARENT);

        switch (view) {
            case "generate":
                if (generatePanel == null) initGeneratePanelView();
                topbarTitle.setText("Generate");
                navGenerate.setTextColor(getColor(R.color.accent));
                navGenerate.setBackgroundResource(R.drawable.bg_nav_active);
                openPanel("generate");
                break;

            case "gallery":
                if (galleryView == null) initGalleryView();
                contentContainer.addView(galleryView);
                topbarTitle.setText("Gallery");
                navGallery.setTextColor(getColor(R.color.accent));
                navGallery.setBackgroundResource(R.drawable.bg_nav_active);
                closePanel();
                loadGalleryData();
                break;

            case "remix":
                if (remixPanel == null) initRemixPanelView();
                topbarTitle.setText("Remix");
                navRemix.setTextColor(getColor(R.color.accent));
                navRemix.setBackgroundResource(R.drawable.bg_nav_active);
                openPanel("remix");
                break;
        }
    }

    // =============================================
    // RIGHT PANEL
    // =============================================

    private void openPanel(String type) {
        panelType = type;
        panelOpen = true;
        panelContainer.removeAllViews();
        panelContainer.setVisibility(View.VISIBLE);

        View panel = "generate".equals(type) ? generatePanel : remixPanel;
        if (panel != null) {
            // Remove from parent first
            if (panel.getParent() != null) {
                ((ViewGroup) panel.getParent()).removeView(panel);
            }
            panelContainer.addView(panel);
        }

        // Animate in
        int pw = getResources().getDisplayMetrics().widthPixels;
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            pw = (int) getResources().getDimension(R.dimen.panel_width);
        }
        panelContainer.setTranslationX(pw);
        ObjectAnimator.ofFloat(panelContainer, "translationX", 0f).setDuration(300).start();
    }

    private void closePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        int pw = getResources().getDisplayMetrics().widthPixels;
        ObjectAnimator anim = ObjectAnimator.ofFloat(panelContainer, "translationX", pw);
        anim.setDuration(300);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                panelContainer.setVisibility(View.GONE);
                panelContainer.removeAllViews();
            }
        });
        anim.start();
    }

    // =============================================
    // GALLERY
    // =============================================

    private void initGallery() {
        // Gallery view is lazily initialized
    }

    private void initGalleryView() {
        galleryView = LayoutInflater.from(this).inflate(R.layout.panel_gallery, contentContainer, false);
        galleryGrid = galleryView.findViewById(R.id.galleryGrid);
        emptyState = galleryView.findViewById(R.id.emptyState);
        tabAll = galleryView.findViewById(R.id.tabAll);
        tabRecent = galleryView.findViewById(R.id.tabRecent);
        tabFavorites = galleryView.findViewById(R.id.tabFavorites);
        imageCount = galleryView.findViewById(R.id.imageCount);
        searchInput = galleryView.findViewById(R.id.searchInput);

        galleryAdapter = new GalleryAdapter(this);
        galleryGrid.setAdapter(galleryAdapter);

        galleryGrid.setOnItemClickListener((parent, view, pos, id) -> {
            KineticDB.ImageEntry entry = galleryAdapter.getItemAt(pos);
            if (entry != null) showViewer(entry);
        });

        // Tab clicks
        tabAll.setOnClickListener(v -> { currentFilter = "all"; updateGalleryTabs(); loadGalleryData(); });
        tabRecent.setOnClickListener(v -> { currentFilter = "recent"; updateGalleryTabs(); loadGalleryData(); });
        tabFavorites.setOnClickListener(v -> { currentFilter = "favorites"; updateGalleryTabs(); loadGalleryData(); });

        // Search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filterGallery(); }
        });
    }

    private void updateGalleryTabs() {
        tabAll.setTextColor(getColor(R.color.text2));
        tabAll.setBackgroundResource(R.drawable.bg_chip);
        tabRecent.setTextColor(getColor(R.color.text2));
        tabRecent.setBackgroundResource(R.drawable.bg_chip);
        tabFavorites.setTextColor(getColor(R.color.text2));
        tabFavorites.setBackgroundResource(R.drawable.bg_chip);

        switch (currentFilter) {
            case "all":
                tabAll.setTextColor(getColor(R.color.accent));
                tabAll.setBackgroundResource(R.drawable.bg_chip_active);
                break;
            case "recent":
                tabRecent.setTextColor(getColor(R.color.accent));
                tabRecent.setBackgroundResource(R.drawable.bg_chip_active);
                break;
            case "favorites":
                tabFavorites.setTextColor(getColor(R.color.accent));
                tabFavorites.setBackgroundResource(R.drawable.bg_chip_active);
                break;
        }
    }

    private void loadGalleryData() {
        new AsyncTask<Void, Void, List<KineticDB.ImageEntry>>() {
            @Override
            protected List<KineticDB.ImageEntry> doInBackground(Void... v) {
                return db.getAll();
            }
            @Override
            protected void onPostExecute(List<KineticDB.ImageEntry> data) {
                allImages = data;
                int favCount = 0;
                for (KineticDB.ImageEntry e : data) if (e.favorite) favCount++;

                tabRecent.setText("Recent (" + Math.min(data.size(), prefs.getRecentLimit()) + ")");
                tabFavorites.setText("Favorites (" + favCount + ")");

                filterGallery();
            }
        }.execute();
    }

    private void filterGallery() {
        if (galleryAdapter == null) return;

        List<KineticDB.ImageEntry> filtered = new ArrayList<>(allImages);

        // Apply filter
        if ("favorites".equals(currentFilter)) {
            List<KineticDB.ImageEntry> favs = new ArrayList<>();
            for (KineticDB.ImageEntry e : filtered) if (e.favorite) favs.add(e);
            filtered = favs;
        } else if ("recent".equals(currentFilter)) {
            int limit = prefs.getRecentLimit();
            if (filtered.size() > limit) {
                filtered = new ArrayList<>(filtered.subList(0, limit));
            }
        }

        // Apply search
        String query = searchInput != null ? searchInput.getText().toString().toLowerCase().trim() : "";
        if (!query.isEmpty()) {
            List<KineticDB.ImageEntry> searched = new ArrayList<>();
            for (KineticDB.ImageEntry e : filtered) {
                if ((e.prompt != null && e.prompt.toLowerCase().contains(query)) ||
                    (e.name != null && e.name.toLowerCase().contains(query)) ||
                    (e.model != null && e.model.toLowerCase().contains(query))) {
                    searched.add(e);
                }
            }
            filtered = searched;
        }

        imageCount.setText(String.valueOf(filtered.size()));
        galleryAdapter.setData(filtered);

        if (emptyState != null) {
            emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // =============================================
    // GENERATE PANEL
    // =============================================

    private void initGeneratePanel() {
        // Panel view is lazily initialized
    }

    private void initGeneratePanelView() {
        generatePanel = LayoutInflater.from(this).inflate(R.layout.panel_generate, panelContainer, false);
        promptInput = generatePanel.findViewById(R.id.promptInput);
        charCount = generatePanel.findViewById(R.id.charCount);
        modelSelector = generatePanel.findViewById(R.id.modelSelector);
        enhanceBtn = generatePanel.findViewById(R.id.enhanceBtn);
        generateBtn = generatePanel.findViewById(R.id.generateBtn);
        shuffleBtn = generatePanel.findViewById(R.id.shuffleBtn);
        styleChipsContainer = generatePanel.findViewById(R.id.styleChips);
        refDropZone = generatePanel.findViewById(R.id.refDropZone);
        refPlaceholder = generatePanel.findViewById(R.id.refPlaceholder);
        refPreview = generatePanel.findViewById(R.id.refPreview);

        // Close button
        generatePanel.findViewById(R.id.genPanelClose).setOnClickListener(v -> {
            closePanel();
            switchContent("gallery");
        });

        // Char count
        promptInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                charCount.setText(String.valueOf(s.length()));
            }
        });

        // Model selector
        selectedModel = prefs.getDefaultModel();
        updateModelSelectorText();
        modelSelector.setOnClickListener(v -> showModelPicker(modelSelector, false));

        // Shuffle button
        shuffleBtn.setOnClickListener(v -> {
            int idx = (int) (Math.random() * RANDOM_PROMPTS.length);
            promptInput.setText(RANDOM_PROMPTS[idx]);
        });

        // Enhance button
        enhanceBtn.setOnClickListener(v -> {
            String prompt = promptInput.getText().toString().trim();
            if (prompt.isEmpty()) {
                showToast(getString(R.string.error_prompt), "error");
                return;
            }
            enhanceBtn.setEnabled(false);
            ((TextView) enhanceBtn).setText("Enhancing...");
            KineticApi.enhancePrompt(prefs.getApiKey(), prompt, "mimo-v2.5-pro",
                prefs.getEnhanceWeb(), new KineticApi.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        promptInput.setText(result);
                        enhanceBtn.setEnabled(true);
                        ((TextView) enhanceBtn).setText("⚡  Enhance Prompt");
                        showToast(getString(R.string.prompt_enhanced), "success");
                    }
                    @Override
                    public void onError(String error) {
                        enhanceBtn.setEnabled(true);
                        ((TextView) enhanceBtn).setText("⚡  Enhance Prompt");
                        showToast(error, "error");
                    }
                });
        });

        // Ratio buttons
        setupRatioButtons(generatePanel, "generate");
        setupCountButtons(generatePanel, "generate");

        // Style chips
        setupStyleChips();

        // Reference image
        refDropZone.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REF);
        });

        // Generate button
        generateBtn.setOnClickListener(v -> handleGenerate());
    }

    private void updateModelSelectorText() {
        String displayName = selectedModel;
        for (Prefs.ModelItem m : BUILT_IN_MODELS) {
            if (m.id.equals(selectedModel)) { displayName = m.name; break; }
        }
        for (Prefs.ModelItem m : prefs.getCustomModels()) {
            if (m.id.equals(selectedModel)) { displayName = m.name; break; }
        }
        modelSelector.setText(displayName);
    }

    private void showModelPicker(View anchor, boolean forRemix) {
        List<Prefs.ModelItem> models = new ArrayList<>();
        for (Prefs.ModelItem m : BUILT_IN_MODELS) models.add(m);
        models.addAll(prefs.getCustomModels());

        String[] names = new String[models.size()];
        for (int i = 0; i < models.size(); i++) {
            names[i] = models.get(i).name + "\n" + models.get(i).id;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Model");
        builder.setItems(names, (dialog, which) -> {
            selectedModel = models.get(which).id;
            updateModelSelectorText();
            dialog.dismiss();
        });
        builder.show();
    }

    // =============================================
    // RATIO & COUNT BUTTONS
    // =============================================

    private void setupRatioButtons(View panel, String type) {
        int[] ids;
        String[] ratios;
        if ("remix".equals(type)) {
            ids = new int[]{R.id.remixRatioSquare, R.id.remixRatioPortrait, R.id.remixRatioLandscape};
            ratios = new String[]{"square", "portrait", "landscape"};
        } else {
            ids = new int[]{R.id.ratioSquare, R.id.ratioPortrait, R.id.ratioLandscape};
            ratios = new String[]{"square", "portrait", "landscape"};
        }

        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            TextView btn = panel.findViewById(ids[i]);
            if (btn == null) continue;
            btn.setOnClickListener(v -> {
                if ("remix".equals(type)) {
                    remixSelectedRatio = ratios[idx];
                } else {
                    selectedRatio = ratios[idx];
                }
                for (int j = 0; j < ids.length; j++) {
                    TextView b = panel.findViewById(ids[j]);
                    if (b == null) continue;
                    boolean active = j == idx;
                    b.setTextColor(getColor(active ? R.color.accent : R.color.text2));
                    b.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
                    if (active) b.setTypeface(null, android.graphics.Typeface.BOLD);
                    else b.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            });
        }
    }

    private void setupCountButtons(View panel, String type) {
        int[] ids;
        if ("remix".equals(type)) {
            ids = new int[]{R.id.remixCount1, R.id.remixCount2, R.id.remixCount3, R.id.remixCount4};
        } else {
            ids = new int[]{R.id.count1, R.id.count2, R.id.count3, R.id.count4};
        }

        for (int i = 0; i < ids.length; i++) {
            final int count = i + 1;
            TextView btn = panel.findViewById(ids[i]);
            if (btn == null) continue;
            btn.setOnClickListener(v -> {
                if ("remix".equals(type)) {
                    remixSelectedCount = count;
                } else {
                    selectedCount = count;
                }
                for (int j = 0; j < ids.length; j++) {
                    TextView b = panel.findViewById(ids[j]);
                    if (b == null) continue;
                    boolean active = (j + 1) == count;
                    b.setTextColor(getColor(active ? R.color.accent : R.color.text2));
                    b.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_count_btn);
                    if (active) b.setTypeface(null, android.graphics.Typeface.BOLD);
                    else b.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            });
        }
    }

    // =============================================
    // STYLE CHIPS
    // =============================================

    private void setupStyleChips() {
        styleChipsContainer.removeAllViews();
        // "None" chip
        addStyleChip("None", true);

        // Add built-in styles from the web version
        String[] styles = {"Sketch", "3D Render", "Watercolor", "Pixel Art", "Anime", "Oil Paint", "Cyberpunk"};
        for (String s : styles) {
            addStyleChip(s, false);
        }
    }

    private void addStyleChip(String name, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(name);
        chip.setTextSize(12);
        chip.setTextColor(getColor(active ? R.color.accent : R.color.text2));
        chip.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(4), 0);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            selectedStyle = name.equals("None") ? "" : name;
            // Update all chips
            for (int i = 0; i < styleChipsContainer.getChildCount(); i++) {
                TextView c = (TextView) styleChipsContainer.getChildAt(i);
                boolean isActive = c.getText().toString().equals(name);
                c.setTextColor(getColor(isActive ? R.color.accent : R.color.text2));
                c.setBackgroundResource(isActive ? R.drawable.bg_chip_active : R.drawable.bg_chip);
            }
        });

        styleChipsContainer.addView(chip);
    }

    // =============================================
    // REMIX PANEL
    // =============================================

    private void initRemixPanel() {
        // Panel view is lazily initialized
    }

    private void initRemixPanelView() {
        remixPanel = LayoutInflater.from(this).inflate(R.layout.panel_remix, panelContainer, false);
        remixPickGrid = remixPanel.findViewById(R.id.remixPickGrid);
        remixUploadZone = remixPanel.findViewById(R.id.remixUploadZone);
        remixSelectedWrap = remixPanel.findViewById(R.id.remixSelectedWrap);
        remixSelectedImg = remixPanel.findViewById(R.id.remixSelectedImg);
        remixPromptInput = remixPanel.findViewById(R.id.remixPromptInput);
        remixBtn = remixPanel.findViewById(R.id.remixBtn);
        actionGridContainer = remixPanel.findViewById(R.id.actionGrid);

        // Close button
        remixPanel.findViewById(R.id.remixPanelClose).setOnClickListener(v -> {
            closePanel();
            switchContent("gallery");
        });

        // Source tabs
        TextView tabGallery = remixPanel.findViewById(R.id.remixTabGallery);
        TextView tabUpload = remixPanel.findViewById(R.id.remixTabUpload);
        tabGallery.setOnClickListener(v -> {
            remixPickGrid.setVisibility(View.VISIBLE);
            remixUploadZone.setVisibility(View.GONE);
            tabGallery.setTextColor(getColor(R.color.accent));
            tabGallery.setBackgroundResource(R.drawable.bg_chip_active);
            tabUpload.setTextColor(getColor(R.color.text2));
            tabUpload.setBackgroundResource(R.drawable.bg_chip);
        });
        tabUpload.setOnClickListener(v -> {
            remixPickGrid.setVisibility(View.GONE);
            remixUploadZone.setVisibility(View.VISIBLE);
            tabUpload.setTextColor(getColor(R.color.accent));
            tabUpload.setBackgroundResource(R.drawable.bg_chip_active);
            tabGallery.setTextColor(getColor(R.color.text2));
            tabGallery.setBackgroundResource(R.drawable.bg_chip);
        });

        // Upload zone click
        remixUploadZone.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REMIX);
        });

        // Clear ref
        remixPanel.findViewById(R.id.remixClearRef).setOnClickListener(v -> {
            remixRefImageBase64 = null;
            remixSelectedWrap.setVisibility(View.GONE);
        });

        // Load gallery pick grid
        loadRemixPickGrid();

        // Action grid
        setupActionGrid();

        // Ratio & count
        setupRatioButtons(remixPanel, "remix");
        setupCountButtons(remixPanel, "remix");

        // Remix button
        remixBtn.setOnClickListener(v -> handleRemix());
    }

    private void loadRemixPickGrid() {
        new AsyncTask<Void, Void, List<KineticDB.ImageEntry>>() {
            @Override
            protected List<KineticDB.ImageEntry> doInBackground(Void... v) {
                return db.getAll();
            }
            @Override
            protected void onPostExecute(List<KineticDB.ImageEntry> data) {
                // Use a simple adapter for the pick grid
                int limit = Math.min(data.size(), 18);
                List<KineticDB.ImageEntry> subset = data.subList(0, limit);
                GalleryAdapter pickAdapter = new GalleryAdapter(MainActivity.this);
                pickAdapter.setData(subset);
                if (remixPickGrid != null) {
                    remixPickGrid.setAdapter(pickAdapter);
                    remixPickGrid.setOnItemClickListener((parent, view, pos, id) -> {
                        KineticDB.ImageEntry entry = pickAdapter.getItemAt(pos);
                        if (entry != null && entry.filePath != null) {
                            setRemixRef(entry.filePath);
                        }
                    });
                }
            }
        }.execute();
    }

    private void setRemixRef(String filePath) {
        try {
            Bitmap bmp = BitmapFactory.decodeFile(filePath);
            if (bmp != null) {
                remixSelectedImg.setImageBitmap(bmp);
                remixSelectedWrap.setVisibility(View.VISIBLE);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                remixRefImageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            }
        } catch (Exception e) {
            showToast("Failed to load image", "error");
        }
    }

    private void setupActionGrid() {
        actionGridContainer.removeAllViews();
        // Create 2-column grid
        LinearLayout row = null;
        for (int i = 0; i < BUILT_IN_ACTIONS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                actionGridContainer.addView(row);
            }

            String[] parts = BUILT_IN_ACTIONS[i].split("\\|");
            String name = parts[0];
            String prompt = parts.length > 1 ? parts[1] : name;

            TextView card = new TextView(this);
            card.setText(name);
            card.setTextSize(13);
            card.setTextColor(getColor(R.color.text2));
            card.setBackgroundResource(R.drawable.bg_btn_ghost);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(8), dp(10), dp(8), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(0, 0, dp(4), dp(4));
            card.setLayoutParams(lp);

            final String actionPrompt = prompt;
            card.setOnClickListener(v -> {
                if (remixPromptInput != null) {
                    remixPromptInput.setText(actionPrompt);
                }
            });

            if (row != null) row.addView(card);
        }
    }

    // =============================================
    // GENERATE HANDLER
    // =============================================

    private void handleGenerate() {
        if (isGenerating) return;

        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            showToast(getString(R.string.error_prompt), "error");
            return;
        }
        if (prefs.getApiKey().isEmpty()) {
            showToast(getString(R.string.error_api_key), "error");
            return;
        }

        isGenerating = true;

        // Show generation overlay
        genOverlay.setVisibility(View.VISIBLE);
        genPromptDisplay.setText(prompt);
        genStatus.setText(getString(R.string.generating));
        genProgressBar.setProgress(0);
        genCounter.setText("0 / " + selectedCount);

        // Determine style suffix
        String fullPrompt = prompt;
        if (!selectedStyle.isEmpty()) {
            fullPrompt += ", " + selectedStyle.toLowerCase() + " style";
        }

        final String batchId = UUID.randomUUID().toString().substring(0, 8);
        final String finalPrompt = fullPrompt;
        final int total = selectedCount;

        generateSequential(0, total, batchId, finalPrompt, prompt);
    }

    private void generateSequential(int index, int total, String batchId,
                                     String fullPrompt, String originalPrompt) {
        if (index >= total) {
            // Done
            genOverlay.setVisibility(View.GONE);
            isGenerating = false;
            showToast(getString(R.string.generation_complete), "success");
            loadGalleryData();
            if ("gallery".equals(currentView)) {
                switchContent("gallery");
            }
            return;
        }

        final int idx = index;
        genStatus.setText("Generating " + (index + 1) + " of " + total + "...");
        genCounter.setText((index) + " / " + total);
        genProgressBar.setProgress((int) ((float) index / total * 100));

        // Optionally enhance first
        if (prefs.getEnhanceEnabled() && index == 0) {
            genStatus.setText(getString(R.string.enhancing));
            KineticApi.enhancePrompt(prefs.getApiKey(), fullPrompt, "mimo-v2.5-pro",
                prefs.getEnhanceWeb(), new KineticApi.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String enhanced) {
                        doGenerate(idx, total, batchId, enhanced, originalPrompt);
                    }
                    @Override
                    public void onError(String error) {
                        doGenerate(idx, total, batchId, fullPrompt, originalPrompt);
                    }
                });
        } else {
            doGenerate(index, total, batchId, fullPrompt, originalPrompt);
        }
    }

    private void doGenerate(int index, int total, String batchId,
                             String fullPrompt, String originalPrompt) {
        genStatus.setText("Generating " + (index + 1) + " of " + total + "...");

        KineticApi.generateImage(prefs.getApiKey(), selectedModel, fullPrompt,
            selectedRatio, refImageBase64, new KineticApi.ApiCallback<Bitmap>() {
                @Override
                public void onSuccess(Bitmap bitmap) {
                    // Save to internal storage
                    String filePath = saveBitmapToInternal(bitmap, batchId, index);

                    // Optionally save to device gallery
                    if (prefs.getSaveImages()) {
                        saveToMediaStore(bitmap, "Kinetic_" + batchId + "_" + index);
                    }

                    // Optionally auto-name
                    if (prefs.getNamingEnabled() && index == 0) {
                        genStatus.setText(getString(R.string.naming));
                        KineticApi.autoName(prefs.getApiKey(), originalPrompt, "nova",
                            new KineticApi.ApiCallback<String>() {
                                @Override
                                public void onSuccess(String name) {
                                    insertEntry(batchId, fullPrompt, originalPrompt, name, filePath, index);
                                    generateSequential(index + 1, total, batchId, fullPrompt, originalPrompt);
                                }
                                @Override
                                public void onError(String e) {
                                    insertEntry(batchId, fullPrompt, originalPrompt, "", filePath, index);
                                    generateSequential(index + 1, total, batchId, fullPrompt, originalPrompt);
                                }
                            });
                    } else {
                        insertEntry(batchId, fullPrompt, originalPrompt, "", filePath, index);
                        generateSequential(index + 1, total, batchId, fullPrompt, originalPrompt);
                    }
                }

                @Override
                public void onError(String error) {
                    genOverlay.setVisibility(View.GONE);
                    isGenerating = false;
                    showToast(getString(R.string.generation_failed) + ": " + error, "error");
                }
            });
    }

    private void insertEntry(String batchId, String prompt, String originalPrompt,
                              String name, String filePath, int index) {
        KineticDB.ImageEntry entry = new KineticDB.ImageEntry();
        entry.prompt = prompt;
        entry.originalPrompt = originalPrompt;
        entry.name = name;
        entry.model = selectedModel;
        entry.ratio = selectedRatio;
        entry.style = selectedStyle;
        entry.filePath = filePath;
        entry.batchId = batchId;
        entry.timestamp = System.currentTimeMillis() + index;
        db.insert(entry);
    }

    private String saveBitmapToInternal(Bitmap bmp, String batchId, int index) {
        try {
            File dir = new File(getFilesDir(), "images");
            dir.mkdirs();
            String filename = "img_" + batchId + "_" + index + ".jpg";
            File file = new File(dir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
            fos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void saveToMediaStore(Bitmap bmp, String name) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Kinetic");

                Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                    os.flush();
                    os.close();
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "Kinetic");
                dir.mkdirs();
                File file = new File(dir, name + ".jpg");
                FileOutputStream fos = new FileOutputStream(file);
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.flush();
                fos.close();
            }
        } catch (Exception e) {
            // Silent fail for gallery save
        }
    }

    // =============================================
    // REMIX HANDLER
    // =============================================

    private void handleRemix() {
        if (isGenerating) return;

        String prompt = remixPromptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            showToast("Enter a remix prompt or select an action", "error");
            return;
        }
        if (remixRefImageBase64 == null) {
            showToast("Select a source image first", "error");
            return;
        }
        if (prefs.getApiKey().isEmpty()) {
            showToast(getString(R.string.error_api_key), "error");
            return;
        }

        isGenerating = true;
        genOverlay.setVisibility(View.VISIBLE);
        genPromptDisplay.setText(prompt);
        genStatus.setText("Remixing...");
        genProgressBar.setIndeterminate(true);

        // Generate with reference image
        String batchId = "remix_" + UUID.randomUUID().toString().substring(0, 6);

        KineticApi.generateImage(prefs.getApiKey(), selectedModel, prompt,
            remixSelectedRatio, remixRefImageBase64, new KineticApi.ApiCallback<Bitmap>() {
                @Override
                public void onSuccess(Bitmap bitmap) {
                    String filePath = saveBitmapToInternal(bitmap, batchId, 0);
                    if (prefs.getSaveImages()) {
                        saveToMediaStore(bitmap, "Kinetic_remix_" + batchId);
                    }

                    KineticDB.ImageEntry entry = new KineticDB.ImageEntry();
                    entry.prompt = prompt;
                    entry.originalPrompt = prompt;
                    entry.name = "Remix";
                    entry.model = selectedModel;
                    entry.ratio = remixSelectedRatio;
                    entry.style = "remix";
                    entry.filePath = filePath;
                    entry.batchId = batchId;
                    entry.timestamp = System.currentTimeMillis();
                    db.insert(entry);

                    genOverlay.setVisibility(View.GONE);
                    genProgressBar.setIndeterminate(false);
                    isGenerating = false;
                    showToast("Remix complete!", "success");
                    loadGalleryData();
                }

                @Override
                public void onError(String error) {
                    genOverlay.setVisibility(View.GONE);
                    genProgressBar.setIndeterminate(false);
                    isGenerating = false;
                    showToast("Remix failed: " + error, "error");
                }
            });
    }

    // =============================================
    // IMAGE VIEWER
    // =============================================

    private void showViewer(KineticDB.ImageEntry entry) {
        Dialog dialog = new Dialog(this, R.style.KineticDialogTheme);
        dialog.setContentView(R.layout.dialog_viewer);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        ImageView viewerImage = dialog.findViewById(R.id.viewerImage);
        TextView viewerPrompt = dialog.findViewById(R.id.viewerPrompt);
        TextView viewerModel = dialog.findViewById(R.id.viewerModel);
        TextView viewerRatio = dialog.findViewById(R.id.viewerRatio);
        ImageView viewerFavBtn = dialog.findViewById(R.id.viewerFavBtn);
        ImageView viewerClose = dialog.findViewById(R.id.viewerClose);
        ImageView viewerFullscreen = dialog.findViewById(R.id.viewerFullscreen);
        ImageView viewerDownload = dialog.findViewById(R.id.viewerDownloadBtn);
        ImageView viewerDelete = dialog.findViewById(R.id.viewerDeleteBtn);
        TextView viewerRemixBtn = dialog.findViewById(R.id.viewerRemixBtn);

        // Load image
        if (entry.filePath != null) {
            Bitmap bmp = BitmapFactory.decodeFile(entry.filePath);
            if (bmp != null) viewerImage.setImageBitmap(bmp);
        }

        // Info
        viewerPrompt.setText(entry.prompt != null ? entry.prompt : "");
        viewerModel.setText(entry.model != null ? entry.model : "");
        viewerRatio.setText(entry.ratio != null ? entry.ratio : "");

        // Favorite icon state
        updateFavIcon(viewerFavBtn, entry.favorite);

        // Close
        viewerClose.setOnClickListener(v -> dialog.dismiss());

        // Fullscreen
        viewerFullscreen.setOnClickListener(v -> {
            dialog.dismiss();
            showFullscreen(entry);
        });

        // Favorite
        viewerFavBtn.setOnClickListener(v -> {
            entry.favorite = !entry.favorite;
            db.toggleFavorite(entry.id, entry.favorite);
            updateFavIcon(viewerFavBtn, entry.favorite);
            showToast(getString(entry.favorite ? R.string.added_to_favorites : R.string.removed_from_favorites),
                "success");
        });

        // Download
        viewerDownload.setOnClickListener(v -> {
            if (entry.filePath != null) {
                Bitmap bmp = BitmapFactory.decodeFile(entry.filePath);
                if (bmp != null) {
                    saveToMediaStore(bmp, "Kinetic_" + entry.id);
                    showToast(getString(R.string.image_saved), "success");
                }
            }
        });

        // Delete
        viewerDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    // Delete file
                    if (entry.filePath != null) {
                        new File(entry.filePath).delete();
                    }
                    db.delete(entry.id);
                    dialog.dismiss();
                    showToast(getString(R.string.image_deleted), "success");
                    loadGalleryData();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        });

        // Remix from viewer
        viewerRemixBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (entry.filePath != null) {
                setRemixRef(entry.filePath);
            }
            switchContent("remix");
        });

        dialog.show();
    }

    private void updateFavIcon(ImageView btn, boolean fav) {
        btn.setImageResource(fav ? R.drawable.ic_fav_filled : R.drawable.ic_fav);
    }

    private void showFullscreen(KineticDB.ImageEntry entry) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF0000000);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (entry.filePath != null) {
            Bitmap bmp = BitmapFactory.decodeFile(entry.filePath);
            if (bmp != null) img.setImageBitmap(bmp);
        }
        root.addView(img, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Close button
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(20);
        close.setTextColor(Color.WHITE);
        close.setPadding(dp(16), dp(16), dp(16), dp(16));
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        closeLp.gravity = Gravity.END | Gravity.TOP;
        root.addView(close, closeLp);

        root.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.show();
    }

    // =============================================
    // SETTINGS DIALOG
    // =============================================

    private void showSettings() {
        Dialog dialog = new Dialog(this, R.style.KineticDialogTheme);
        dialog.setContentView(R.layout.dialog_settings);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        // Close
        dialog.findViewById(R.id.settingsClose).setOnClickListener(v -> dialog.dismiss());

        // Profile
        EditText settingsName = dialog.findViewById(R.id.settingsName);
        settingsName.setText(prefs.getName());

        // API Key
        EditText settingsApiKey = dialog.findViewById(R.id.settingsApiKey);
        settingsApiKey.setText(prefs.getApiKey());

        // Theme
        TextView themeOrange = dialog.findViewById(R.id.themeOrange);
        TextView themeBlue = dialog.findViewById(R.id.themeBlue);
        setupThemeButtons(themeOrange, themeBlue, prefs.getTheme());

        themeOrange.setOnClickListener(v -> {
            prefs.setTheme("orange");
            setupThemeButtons(themeOrange, themeBlue, "orange");
        });
        themeBlue.setOnClickListener(v -> {
            prefs.setTheme("blue");
            setupThemeButtons(themeOrange, themeBlue, "blue");
        });

        // Animations
        Switch switchAnimations = dialog.findViewById(R.id.switchAnimations);
        switchAnimations.setChecked(prefs.getAnimations());
        switchAnimations.setOnCheckedChangeListener((b, checked) -> prefs.setAnimations(checked));

        // Enhancement
        Switch switchEnhance = dialog.findViewById(R.id.switchEnhance);
        switchEnhance.setChecked(prefs.getEnhanceEnabled());
        switchEnhance.setOnCheckedChangeListener((b, checked) -> prefs.setEnhanceEnabled(checked));

        Switch switchWebSearch = dialog.findViewById(R.id.switchWebSearch);
        switchWebSearch.setChecked(prefs.getEnhanceWeb());
        switchWebSearch.setOnCheckedChangeListener((b, checked) -> prefs.setEnhanceWeb(checked));

        // Naming
        Switch switchNaming = dialog.findViewById(R.id.switchNaming);
        switchNaming.setChecked(prefs.getNamingEnabled());
        switchNaming.setOnCheckedChangeListener((b, checked) -> prefs.setNamingEnabled(checked));

        // Model list
        LinearLayout modelListContainer = dialog.findViewById(R.id.modelListContainer);
        refreshModelList(modelListContainer);

        // Add model
        EditText addModelId = dialog.findViewById(R.id.addModelId);
        EditText addModelName = dialog.findViewById(R.id.addModelName);
        dialog.findViewById(R.id.btnAddModel).setOnClickListener(v -> {
            String id = addModelId.getText().toString().trim();
            String name = addModelName.getText().toString().trim();
            if (id.isEmpty()) {
                addModelId.setError("Required");
                return;
            }
            if (name.isEmpty()) name = id;

            List<Prefs.ModelItem> customs = prefs.getCustomModels();
            customs.add(new Prefs.ModelItem(id, name, false, true));
            prefs.setCustomModels(customs);

            addModelId.setText("");
            addModelName.setText("");
            refreshModelList(modelListContainer);
            showToast("Model added: " + name, "success");
        });

        // Delete models button
        dialog.findViewById(R.id.btnDeleteModels).setOnClickListener(v -> {
            showDeleteModelsDialog(modelListContainer);
        });

        // Save
        dialog.findViewById(R.id.settingsSave).setOnClickListener(v -> {
            String name = settingsName.getText().toString().trim();
            String apiKey = settingsApiKey.getText().toString().trim();
            if (!name.isEmpty()) prefs.setName(name);
            if (!apiKey.isEmpty()) prefs.setApiKey(apiKey);
            updateAvatar();
            dialog.dismiss();
            showToast("Settings saved", "success");
        });

        dialog.show();
    }

    private void setupThemeButtons(TextView orange, TextView blue, String theme) {
        boolean isOrange = "orange".equals(theme);
        orange.setTextColor(getColor(isOrange ? R.color.accent : R.color.text2));
        orange.setBackgroundResource(isOrange ? R.drawable.bg_chip_active : R.drawable.bg_chip);
        blue.setTextColor(getColor(!isOrange ? R.color.accent : R.color.text2));
        blue.setBackgroundResource(!isOrange ? R.drawable.bg_chip_active : R.drawable.bg_chip);
    }

    private void refreshModelList(LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // Built-in models
        for (Prefs.ModelItem m : BUILT_IN_MODELS) {
            View row = inflater.inflate(R.layout.item_model, container, false);
            ((TextView) row.findViewById(R.id.modelName)).setText(m.name);
            ((TextView) row.findViewById(R.id.modelId)).setText(m.id + " (built-in)");
            row.findViewById(R.id.modelCheck).setVisibility(View.GONE);
            if (m.refImages) {
                row.findViewById(R.id.modelRefBadge).setVisibility(View.VISIBLE);
            }
            container.addView(row);
        }

        // Custom models
        for (Prefs.ModelItem m : prefs.getCustomModels()) {
            View row = inflater.inflate(R.layout.item_model, container, false);
            ((TextView) row.findViewById(R.id.modelName)).setText(m.name);
            ((TextView) row.findViewById(R.id.modelId)).setText(m.id + " (custom)");
            row.findViewById(R.id.modelCheck).setVisibility(View.GONE);
            container.addView(row);
        }
    }

    // =============================================
    // DELETE MODELS DIALOG
    // =============================================

    private void showDeleteModelsDialog(LinearLayout parentContainer) {
        List<Prefs.ModelItem> customs = prefs.getCustomModels();

        Dialog dialog = new Dialog(this, R.style.KineticDialogTheme);
        dialog.setContentView(R.layout.dialog_delete_models);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        LinearLayout deleteList = dialog.findViewById(R.id.deleteModelList);
        TextView emptyText = dialog.findViewById(R.id.deleteModelsEmpty);

        if (customs.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            deleteList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            deleteList.setVisibility(View.VISIBLE);

            List<CheckBox> checkBoxes = new ArrayList<>();
            for (Prefs.ModelItem m : customs) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(8), dp(8), dp(8));
                row.setBackgroundResource(R.drawable.bg_card);

                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
                row.addView(cb);
                checkBoxes.add(cb);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                infoLp.setMarginStart(dp(8));
                info.setLayoutParams(infoLp);

                TextView nameTv = new TextView(this);
                nameTv.setText(m.name);
                nameTv.setTextColor(getColor(R.color.text));
                nameTv.setTextSize(14);
                info.addView(nameTv);

                TextView idTv = new TextView(this);
                idTv.setText(m.id);
                idTv.setTextColor(getColor(R.color.accent));
                idTv.setTextSize(11);
                info.addView(idTv);

                row.addView(info);

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp(4));
                row.setLayoutParams(rowLp);
                deleteList.addView(row);
            }

            // Confirm delete
            dialog.findViewById(R.id.deleteModelsConfirm).setOnClickListener(v -> {
                List<Prefs.ModelItem> toKeep = new ArrayList<>();
                for (int i = 0; i < customs.size(); i++) {
                    if (!checkBoxes.get(i).isChecked()) {
                        toKeep.add(customs.get(i));
                    }
                }
                int deleted = customs.size() - toKeep.size();
                prefs.setCustomModels(toKeep);
                if (parentContainer != null) refreshModelList(parentContainer);
                dialog.dismiss();
                if (deleted > 0) {
                    showToast(deleted + " model(s) deleted", "success");
                } else {
                    showToast("No models selected", "info");
                }
            });
        }

        // Cancel
        dialog.findViewById(R.id.deleteModelsCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // =============================================
    // IMAGE PICK RESULTS
    // =============================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();

            if (bmp == null) return;

            // Resize if too large
            int maxDim = 1024;
            if (bmp.getWidth() > maxDim || bmp.getHeight() > maxDim) {
                float scale = Math.min((float) maxDim / bmp.getWidth(),
                    (float) maxDim / bmp.getHeight());
                bmp = Bitmap.createScaledBitmap(bmp,
                    (int) (bmp.getWidth() * scale),
                    (int) (bmp.getHeight() * scale), true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            if (requestCode == PICK_IMAGE_REF) {
                refImageBase64 = b64;
                if (refPreview != null) {
                    refPreview.setImageBitmap(bmp);
                    refPreview.setVisibility(View.VISIBLE);
                    if (refPlaceholder != null) refPlaceholder.setVisibility(View.GONE);
                }
            } else if (requestCode == PICK_IMAGE_REMIX) {
                remixRefImageBase64 = b64;
                if (remixSelectedImg != null) {
                    remixSelectedImg.setImageBitmap(bmp);
                    remixSelectedWrap.setVisibility(View.VISIBLE);
                }
            } else if (requestCode == PICK_IMAGE_AVATAR) {
                ByteArrayOutputStream avatarBaos = new ByteArrayOutputStream();
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 256, 256, true);
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, avatarBaos);
                String avatarB64 = Base64.encodeToString(avatarBaos.toByteArray(), Base64.NO_WRAP);
                prefs.setAvatar(avatarB64);
                updateAvatar();
            }
        } catch (Exception e) {
            showToast("Failed to load image", "error");
        }
    }

    // =============================================
    // TOAST
    // =============================================

    private void showToast(String message, String type) {
        View toastView = LayoutInflater.from(this).inflate(
            android.R.layout.simple_list_item_1, null);
        // Use Android toast for simplicity
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // =============================================
    // HELPERS
    // =============================================

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) {
            closePanel();
            switchContent("gallery");
        } else if (sidebarOpen) {
            closeSidebar();
        } else if (!"gallery".equals(currentView)) {
            switchContent("gallery");
        } else {
            super.onBackPressed();
        }
    }
}
