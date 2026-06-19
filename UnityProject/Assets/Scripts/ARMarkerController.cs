using System.Collections;
using UnityEngine;
using Vuforia;

[RequireComponent(typeof(ObserverBehaviour))]
public class ARMarkerController : MonoBehaviour
{
    [SerializeField] private string markerCode = "BIO_CELL_001";
    [SerializeField] private ARContentApiClient apiClient;
    [SerializeField] private ARInfoPanelController infoPanel;
    [SerializeField] private ModelController modelController;

    private ObserverBehaviour observer;
    private Coroutine resolveRoutine;

    private void Awake()
    {
        observer = GetComponent<ObserverBehaviour>();
        if (modelController == null) modelController = GetComponent<ModelController>();
        if (modelController == null) modelController = gameObject.AddComponent<ModelController>();
    }

    private void Start()
    {
        InitializeModel();
    }

    public void Configure(
        string code,
        ARContentApiClient contentApiClient,
        ARInfoPanelController contentInfoPanel,
        ModelController targetModelController)
    {
        markerCode = code;
        apiClient = contentApiClient;
        infoPanel = contentInfoPanel;
        modelController = targetModelController != null
            ? targetModelController
            : GetComponent<ModelController>();
        InitializeModel();
    }

    private void InitializeModel()
    {
        if (modelController == null) return;
        modelController.EnsureModel(markerCode);
        modelController.SetVisible(false);
    }

    private void OnEnable()
    {
        if (observer == null) observer = GetComponent<ObserverBehaviour>();
        if (observer != null) observer.OnTargetStatusChanged += OnTargetStatusChanged;
    }

    private void OnDisable()
    {
        if (observer != null) observer.OnTargetStatusChanged -= OnTargetStatusChanged;
    }

    private void OnTargetStatusChanged(ObserverBehaviour behaviour, TargetStatus status)
    {
        var tracked = status.Status == Status.TRACKED || status.Status == Status.EXTENDED_TRACKED;
        if (tracked)
        {
            Debug.Log($"[ARMarkerController] Marker detected: {markerCode}");
            modelController.SetVisible(true);
            ResolveContent();
        }
        else
        {
            Debug.Log($"[ARMarkerController] Marker lost: {markerCode}");
            modelController.SetVisible(false);
            infoPanel?.Hide();
        }
    }

    private void ResolveContent()
    {
        if (resolveRoutine != null) StopCoroutine(resolveRoutine);
        resolveRoutine = StartCoroutine(ResolveContentRoutine());
    }

    private IEnumerator ResolveContentRoutine()
    {
        if (apiClient == null)
        {
            Debug.LogWarning("[ARMarkerController] API client missing. Using fallback data.");
            infoPanel?.Show(DemoFallbackData.Resolve(markerCode), modelController);
            yield break;
        }

        var done = false;
        ARContentResponse content = null;
        string error = null;

        yield return apiClient.ResolveMarker(markerCode,
            response =>
            {
                content = response;
                done = true;
            },
            message =>
            {
                error = message;
                done = true;
            });

        if (!done || content == null)
        {
            Debug.LogWarning($"[ARMarkerController] API failed for {markerCode}. Fallback. Error: {error}");
            content = DemoFallbackData.Resolve(markerCode);
            content.description = "[OFFLINE DEMO] " + content.description;
        }

        modelController.EnsureModel(markerCode);
        infoPanel?.Show(content, modelController);
    }
}
