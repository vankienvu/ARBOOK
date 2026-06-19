using UnityEngine;

public class ModelController : MonoBehaviour
{
    [SerializeField] private string markerCode;
    [SerializeField] private bool hideWhenTargetLost = true;

    private GameObject modelRoot;
    private Vector3 initialPosition;
    private Quaternion initialRotation;
    private Vector3 initialScale;
    private bool rotating;

    public void EnsureModel(string code)
    {
        markerCode = code;
        if (modelRoot != null) return;

        try
        {
            modelRoot = PlaceholderModelFactory.Create(markerCode);
            modelRoot.transform.SetParent(transform, false);
            modelRoot.transform.localPosition = Vector3.zero;
            modelRoot.transform.localRotation = Quaternion.identity;
            modelRoot.transform.localScale = Vector3.one * ResolveDisplayScale(markerCode);
            initialPosition = modelRoot.transform.localPosition;
            initialRotation = modelRoot.transform.localRotation;
            initialScale = modelRoot.transform.localScale;
            Debug.Log($"[ModelController] Load model success for {markerCode}: placeholder primitive model created.");
        }
        catch (System.Exception ex)
        {
            Debug.LogError($"[ModelController] Load model failed for {markerCode}: {ex.Message}");
        }
    }

    private static float ResolveDisplayScale(string code)
    {
        return code switch
        {
            "SOLAR_SYSTEM_001" => 0.10f,
            "HEART_001" => 0.16f,
            _ => 0.16f
        };
    }

    public void SetVisible(bool visible)
    {
        if (modelRoot == null) return;
        if (!visible && !hideWhenTargetLost) return;
        modelRoot.SetActive(visible);
    }

    public void PlayAnimation()
    {
        rotating = true;
        Debug.Log($"[ModelController] Play animation for {markerCode}");
    }

    public void ResetModel()
    {
        rotating = false;
        if (modelRoot == null) return;
        modelRoot.transform.localPosition = initialPosition;
        modelRoot.transform.localRotation = initialRotation;
        modelRoot.transform.localScale = initialScale;
        Debug.Log($"[ModelController] Reset model for {markerCode}");
    }

    private void Update()
    {
        if (rotating && modelRoot != null && modelRoot.activeSelf)
        {
            modelRoot.transform.Rotate(Vector3.up, 45f * Time.deltaTime, Space.Self);
        }
    }
}
