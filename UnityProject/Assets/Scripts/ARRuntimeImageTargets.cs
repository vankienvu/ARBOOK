using UnityEngine;
using Vuforia;

public class ARRuntimeImageTargets : MonoBehaviour
{
    [SerializeField] private Texture2D bioCellTarget;
    [SerializeField] private Texture2D solarSystemTarget;
    [SerializeField] private Texture2D heartTarget;
    [SerializeField] private float targetWidthMeters = 0.18f;
    [SerializeField] private ARContentApiClient apiClient;
    [SerializeField] private ARInfoPanelController infoPanel;

    private bool targetsCreated;

    public void Configure(
        Texture2D bioCell,
        Texture2D solarSystem,
        Texture2D heart,
        ARContentApiClient contentApiClient,
        ARInfoPanelController contentInfoPanel)
    {
        bioCellTarget = bioCell;
        solarSystemTarget = solarSystem;
        heartTarget = heart;
        apiClient = contentApiClient;
        infoPanel = contentInfoPanel;
    }

    private void Awake()
    {
        VuforiaApplication.Instance.OnVuforiaStarted += CreateTargets;
    }

    private void Start()
    {
        if (VuforiaApplication.Instance.IsRunning) CreateTargets();
    }

    private void OnDestroy()
    {
        VuforiaApplication.Instance.OnVuforiaStarted -= CreateTargets;
    }

    private void CreateTargets()
    {
        if (targetsCreated) return;

        var vuforia = VuforiaBehaviour.Instance;
        if (vuforia == null)
        {
            Debug.LogError("[ARRuntimeImageTargets] VuforiaBehaviour is missing from the AR Camera.");
            return;
        }

        vuforia.SetMaximumSimultaneousTrackedImages(3);
        CreateTarget(vuforia, bioCellTarget, "BIO_CELL_001");
        CreateTarget(vuforia, solarSystemTarget, "SOLAR_SYSTEM_001");
        CreateTarget(vuforia, heartTarget, "HEART_001");
        targetsCreated = true;
    }

    private void CreateTarget(VuforiaBehaviour vuforia, Texture2D texture, string markerCode)
    {
        if (texture == null)
        {
            Debug.LogError($"[ARRuntimeImageTargets] Texture missing for {markerCode}.");
            return;
        }

        var target = vuforia.ObserverFactory.CreateImageTarget(texture, targetWidthMeters, markerCode);
        if (target == null)
        {
            Debug.LogError($"[ARRuntimeImageTargets] Failed to create Image Target {markerCode}.");
            return;
        }

        target.gameObject.name = $"ImageTarget_{markerCode}";
        var modelController = target.gameObject.AddComponent<ModelController>();
        var markerController = target.gameObject.AddComponent<ARMarkerController>();
        markerController.Configure(markerCode, apiClient, infoPanel, modelController);
        Debug.Log($"[ARRuntimeImageTargets] Image Target ready: {markerCode}");
    }
}
