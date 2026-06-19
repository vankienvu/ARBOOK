using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.SceneManagement;
using UnityEngine.UI;
using Vuforia;
using UIImage = UnityEngine.UI.Image;

public static class ARTextbookSceneSetup
{
    private const string ScenePath = "Assets/Scenes/ARTextbookDemo.unity";
    private const string VuforiaConfigurationPath = "Assets/Resources/VuforiaConfiguration.asset";

    private static readonly Color PageColor = new(0.92f, 0.96f, 0.94f, 1f);
    private static readonly Color HeaderColor = new(0.03f, 0.04f, 0.04f, 0.88f);
    private static readonly Color PanelColor = new(0.04f, 0.055f, 0.052f, 0.92f);
    private static readonly Color AccentColor = new(0.05f, 0.70f, 0.57f, 1f);
    private static readonly Color SecondaryColor = new(0.95f, 0.43f, 0.30f, 1f);
    private static readonly Color AmberColor = new(0.95f, 0.72f, 0.22f, 1f);
    private static readonly Color MutedTextColor = new(0.78f, 0.85f, 0.83f, 1f);

    [MenuItem("AR Textbook/Build Demo Scene")]
    public static void BuildScene()
    {
        PrepareTexture("Assets/ImageTargets/BIO_CELL_001/BIO_CELL_001.png");
        PrepareTexture("Assets/ImageTargets/SOLAR_SYSTEM_001/SOLAR_SYSTEM_001.png");
        PrepareTexture("Assets/ImageTargets/HEART_001/HEART_001.png");

        var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
        CreateCamera();
        CreateLight();

        var apiObject = new GameObject("AR Services");
        var apiClient = apiObject.AddComponent<ARContentApiClient>();

        var infoPanel = CreateInterface();
        var targetManager = apiObject.AddComponent<ARRuntimeImageTargets>();
        targetManager.Configure(
            LoadTexture("Assets/ImageTargets/BIO_CELL_001/BIO_CELL_001.png"),
            LoadTexture("Assets/ImageTargets/SOLAR_SYSTEM_001/SOLAR_SYSTEM_001.png"),
            LoadTexture("Assets/ImageTargets/HEART_001/HEART_001.png"),
            apiClient,
            infoPanel);

        ConfigureVuforia();
        EditorSceneManager.SaveScene(scene, ScenePath);
        AddSceneToBuildSettings();
        AssetDatabase.SaveAssets();

        Debug.Log("[ARTextbookSceneSetup] ARTextbookDemo scene created successfully.");
    }

    [InitializeOnLoadMethod]
    private static void OpenDemoSceneWhenEditorStarts()
    {
        if (Application.isBatchMode) return;

        EditorApplication.delayCall += () =>
        {
            var activeScene = SceneManager.GetActiveScene();
            if (!activeScene.isDirty &&
                string.IsNullOrEmpty(activeScene.path) &&
                AssetDatabase.LoadAssetAtPath<SceneAsset>(ScenePath) != null)
            {
                EditorSceneManager.OpenScene(ScenePath);
            }

            if (SceneManager.GetActiveScene().path != ScenePath) return;
            var configuration = AssetDatabase.LoadAssetAtPath<VuforiaConfiguration>(VuforiaConfigurationPath);
            if (configuration != null)
            {
                Selection.activeObject = configuration;
                EditorGUIUtility.PingObject(configuration);
            }
        };
    }

    private static void CreateCamera()
    {
        var cameraObject = new GameObject("ARCamera");
        cameraObject.tag = "MainCamera";
        var camera = cameraObject.AddComponent<Camera>();
        camera.clearFlags = CameraClearFlags.SolidColor;
        camera.backgroundColor = PageColor;
        camera.nearClipPlane = 0.01f;
        camera.farClipPlane = 100f;
        cameraObject.AddComponent<AudioListener>();
        cameraObject.AddComponent<VuforiaBehaviour>();
        cameraObject.AddComponent<DefaultInitializationErrorHandler>();
    }

    private static void CreateLight()
    {
        var lightObject = new GameObject("Demo Light");
        var light = lightObject.AddComponent<Light>();
        light.type = LightType.Directional;
        light.color = new Color(1f, 0.96f, 0.9f);
        light.intensity = 1.15f;
        lightObject.transform.rotation = Quaternion.Euler(48f, -32f, 0f);
    }

    private static ARInfoPanelController CreateInterface()
    {
        var canvasObject = new GameObject("AR Interface");
        var canvas = canvasObject.AddComponent<Canvas>();
        canvas.renderMode = RenderMode.ScreenSpaceOverlay;
        var scaler = canvasObject.AddComponent<CanvasScaler>();
        scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
        scaler.referenceResolution = new Vector2(1080f, 1920f);
        scaler.matchWidthOrHeight = 0.5f;
        canvasObject.AddComponent<GraphicRaycaster>();

        var header = CreatePanel("Header", canvasObject.transform, HeaderColor);
        SetRect(header.rectTransform, new Vector2(0f, 1f), Vector2.one, new Vector2(0f, -92f), Vector2.zero);
        CreatePanel("Brand Cube", header.transform, AccentColor).rectTransform.sizeDelta = new Vector2(34f, 34f);
        var brandCube = header.transform.Find("Brand Cube").GetComponent<RectTransform>();
        SetRect(brandCube, new Vector2(0f, 0.5f), new Vector2(0f, 0.5f), new Vector2(30f, -17f), new Vector2(64f, 17f));
        CreateText("Brand", header.transform, "AR Textbook", 26, FontStyle.Bold, Color.white,
            new Vector2(0f, 0f), new Vector2(0.62f, 1f), new Vector2(82f, 0f), new Vector2(-8f, 0f));
        var statusPill = CreatePanel("Status Pill", header.transform, new Color(0.04f, 0.20f, 0.18f, 0.95f));
        SetRect(statusPill.rectTransform, new Vector2(1f, 0.5f), new Vector2(1f, 0.5f), new Vector2(-210f, -24f), new Vector2(-30f, 24f));
        var status = CreateText("Status", statusPill.transform, "SCAN READY", 15, FontStyle.Bold, AccentColor,
            Vector2.zero, Vector2.one, new Vector2(14f, 0f), new Vector2(-14f, 0f));
        status.alignment = TextAnchor.MiddleCenter;

        var coach = CreatePanel("Scan Coach", canvasObject.transform, new Color(0.04f, 0.055f, 0.052f, 0.68f));
        SetRect(coach.rectTransform, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(26f, -198f), new Vector2(-26f, -116f));
        var coachTitle = CreateText("Coach Title", coach.transform, "Đưa marker vào khung", 22, FontStyle.Bold, Color.white,
            Vector2.zero, Vector2.one, new Vector2(22f, 38f), new Vector2(-22f, -8f));
        coachTitle.alignment = TextAnchor.MiddleLeft;
        var coachHint = CreateText("Coach Hint", coach.transform, "BIO_CELL_001  •  SOLAR_SYSTEM_001  •  HEART_001", 14, FontStyle.Bold, AmberColor,
            Vector2.zero, Vector2.one, new Vector2(22f, 8f), new Vector2(-22f, -48f));
        coachHint.alignment = TextAnchor.MiddleLeft;

        CreateScanReticle(canvasObject.transform);

        var panel = CreatePanel("AR Info Panel", canvasObject.transform, PanelColor);
        SetRect(panel.rectTransform, Vector2.zero, new Vector2(1f, 0f), new Vector2(26f, 30f), new Vector2(-26f, 390f));

        var stripe = CreatePanel("Accent Stripe", panel.transform, AccentColor);
        SetRect(stripe.rectTransform, new Vector2(0f, 1f), Vector2.one, new Vector2(0f, -8f), Vector2.zero);

        var marker = CreateText("Marker Code", panel.transform, "MARKER", 15, FontStyle.Bold, AccentColor,
            new Vector2(0f, 1f), Vector2.one, new Vector2(28f, -58f), new Vector2(-28f, -24f));
        var lesson = CreateText("Lesson Title", panel.transform, "Lesson", 31, FontStyle.Bold, Color.white,
            new Vector2(0f, 1f), Vector2.one, new Vector2(28f, -112f), new Vector2(-28f, -58f));
        var content = CreateText("Content Title", panel.transform, "AR content", 20, FontStyle.Bold, AmberColor,
            new Vector2(0f, 1f), Vector2.one, new Vector2(28f, -154f), new Vector2(-28f, -112f));
        var divider = CreatePanel("Divider", panel.transform, new Color(1f, 1f, 1f, 0.14f));
        SetRect(divider.rectTransform, new Vector2(0f, 1f), Vector2.one, new Vector2(28f, -172f), new Vector2(-28f, -170f));
        var description = CreateText("Description", panel.transform, string.Empty, 17, FontStyle.Normal,
            MutedTextColor, Vector2.zero, Vector2.one,
            new Vector2(28f, 100f), new Vector2(-28f, -188f));
        description.alignment = TextAnchor.UpperLeft;

        var playButton = CreateButton("Play Animation", panel.transform, "Play 3D", AccentColor,
            Vector2.zero, new Vector2(0.5f, 0f), new Vector2(28f, 28f), new Vector2(-8f, 86f));
        var resetButton = CreateButton("Reset Model", panel.transform, "Reset", new Color(0.16f, 0.20f, 0.20f, 1f),
            new Vector2(0.5f, 0f), Vector2.right, new Vector2(8f, 28f), new Vector2(-28f, 86f));

        var controller = panel.gameObject.AddComponent<ARInfoPanelController>();
        controller.Configure(marker, lesson, content, description, playButton, resetButton);
        panel.gameObject.SetActive(false);

        var eventSystem = new GameObject("EventSystem");
        eventSystem.AddComponent<EventSystem>();
        eventSystem.AddComponent<StandaloneInputModule>();

        return controller;
    }

    private static void CreateScanReticle(Transform parent)
    {
        var reticle = new GameObject("Scan Frame", typeof(RectTransform));
        reticle.transform.SetParent(parent, false);
        SetRect(
            reticle.GetComponent<RectTransform>(),
            new Vector2(0.5f, 0.5f),
            new Vector2(0.5f, 0.5f),
            new Vector2(-190f, -190f),
            new Vector2(190f, 190f));

        CreateCorner("Top Left", reticle.transform, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, -72f), new Vector2(8f, 0f));
        CreateCorner("Top Left H", reticle.transform, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, -8f), new Vector2(72f, 0f));
        CreateCorner("Top Right", reticle.transform, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-8f, -72f), new Vector2(0f, 0f));
        CreateCorner("Top Right H", reticle.transform, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-72f, -8f), new Vector2(0f, 0f));
        CreateCorner("Bottom Left", reticle.transform, Vector2.zero, Vector2.zero, new Vector2(0f, 0f), new Vector2(8f, 72f));
        CreateCorner("Bottom Left H", reticle.transform, Vector2.zero, Vector2.zero, new Vector2(0f, 0f), new Vector2(72f, 8f));
        CreateCorner("Bottom Right", reticle.transform, Vector2.one, Vector2.one, new Vector2(-8f, -72f), new Vector2(0f, 0f));
        CreateCorner("Bottom Right H", reticle.transform, Vector2.one, Vector2.one, new Vector2(-72f, -8f), new Vector2(0f, 0f));

        var center = CreateText("Reticle Label", reticle.transform, "SCAN", 18, FontStyle.Bold, new Color(1f, 1f, 1f, 0.64f),
            Vector2.zero, Vector2.one, new Vector2(0f, -16f), new Vector2(0f, 16f));
        center.alignment = TextAnchor.MiddleCenter;
    }

    private static void CreateCorner(
        string name,
        Transform parent,
        Vector2 anchorMin,
        Vector2 anchorMax,
        Vector2 offsetMin,
        Vector2 offsetMax)
    {
        var corner = CreatePanel(name, parent, AccentColor);
        SetRect(corner.rectTransform, anchorMin, anchorMax, offsetMin, offsetMax);
    }

    private static UIImage CreatePanel(string name, Transform parent, Color color)
    {
        var panel = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(UIImage));
        panel.transform.SetParent(parent, false);
        var image = panel.GetComponent<UIImage>();
        image.color = color;
        return image;
    }

    private static Text CreateText(
        string name,
        Transform parent,
        string value,
        int fontSize,
        FontStyle style,
        Color color,
        Vector2 anchorMin,
        Vector2 anchorMax,
        Vector2 offsetMin,
        Vector2 offsetMax)
    {
        var textObject = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(Text));
        textObject.transform.SetParent(parent, false);
        var text = textObject.GetComponent<Text>();
        text.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        text.text = value;
        text.fontSize = fontSize;
        text.fontStyle = style;
        text.color = color;
        text.alignment = TextAnchor.MiddleLeft;
        text.horizontalOverflow = HorizontalWrapMode.Wrap;
        text.verticalOverflow = VerticalWrapMode.Truncate;
        SetRect(text.rectTransform, anchorMin, anchorMax, offsetMin, offsetMax);
        return text;
    }

    private static Button CreateButton(
        string name,
        Transform parent,
        string label,
        Color color,
        Vector2 anchorMin,
        Vector2 anchorMax,
        Vector2 offsetMin,
        Vector2 offsetMax)
    {
        var image = CreatePanel(name, parent, color);
        SetRect(image.rectTransform, anchorMin, anchorMax, offsetMin, offsetMax);
        var button = image.gameObject.AddComponent<Button>();
        button.targetGraphic = image;
        var colors = button.colors;
        colors.highlightedColor = Color.Lerp(color, Color.white, 0.14f);
        colors.pressedColor = Color.Lerp(color, Color.black, 0.18f);
        button.colors = colors;

        var text = CreateText("Label", image.transform, label, 16, FontStyle.Bold, Color.white,
            Vector2.zero, Vector2.one, new Vector2(10f, 4f), new Vector2(-10f, -4f));
        text.alignment = TextAnchor.MiddleCenter;
        return button;
    }

    private static void SetRect(
        RectTransform rect,
        Vector2 anchorMin,
        Vector2 anchorMax,
        Vector2 offsetMin,
        Vector2 offsetMax)
    {
        rect.anchorMin = anchorMin;
        rect.anchorMax = anchorMax;
        rect.offsetMin = offsetMin;
        rect.offsetMax = offsetMax;
    }

    private static Texture2D LoadTexture(string path)
    {
        return AssetDatabase.LoadAssetAtPath<Texture2D>(path);
    }

    private static void PrepareTexture(string path)
    {
        var importer = AssetImporter.GetAtPath(path) as TextureImporter;
        if (importer == null) return;

        importer.textureType = TextureImporterType.Default;
        importer.isReadable = true;
        importer.mipmapEnabled = false;
        importer.textureCompression = TextureImporterCompression.Uncompressed;
        importer.maxTextureSize = 2048;
        importer.SaveAndReimport();
    }

    private static void ConfigureVuforia()
    {
        if (!AssetDatabase.IsValidFolder("Assets/Resources"))
            AssetDatabase.CreateFolder("Assets", "Resources");

        var configuration = AssetDatabase.LoadAssetAtPath<VuforiaConfiguration>(VuforiaConfigurationPath);
        if (configuration == null)
        {
            configuration = ScriptableObject.CreateInstance<VuforiaConfiguration>();
            AssetDatabase.CreateAsset(configuration, VuforiaConfigurationPath);
        }

        configuration.PlayMode.PlayModeType = PlayModeType.WEBCAM;
        configuration.Vuforia.MaxSimultaneousImageTargets = 3;
        EditorUtility.SetDirty(configuration);
        AssetDatabase.SaveAssets();
        Debug.Log(
            $"[ARTextbookSceneSetup] Vuforia config path: {AssetDatabase.GetAssetPath(configuration)}, " +
            $"play mode: {configuration.PlayMode.PlayModeType}, " +
            $"max image targets: {configuration.Vuforia.MaxSimultaneousImageTargets}, " +
            $"license configured: {!string.IsNullOrWhiteSpace(configuration.Vuforia.LicenseKey)}");
    }

    private static void AddSceneToBuildSettings()
    {
        var scenes = EditorBuildSettings.scenes.ToList();
        if (scenes.All(scene => scene.path != ScenePath))
            scenes.Add(new EditorBuildSettingsScene(ScenePath, true));
        EditorBuildSettings.scenes = scenes.ToArray();
    }
}
