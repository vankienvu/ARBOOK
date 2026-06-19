using System;
using System.Collections;
using UnityEngine;
using UnityEngine.Networking;

public class ARContentApiClient : MonoBehaviour
{
    [SerializeField] private string backendBaseUrl = "http://localhost:8080";
    [SerializeField] private string androidBackendBaseUrl = "http://192.168.10.3:8080";
    [SerializeField] private string unityClientKey = "UnitySecretKey123";
    [SerializeField] private int timeoutSeconds = 5;

    public IEnumerator ResolveMarker(
        string markerCode,
        Action<ARContentResponse> onSuccess,
        Action<string> onError)
    {
        var url = $"{ResolveBackendBaseUrl().TrimEnd('/')}/api/ar-markers/resolve?code={UnityWebRequest.EscapeURL(markerCode)}";
        Debug.Log($"[ARContentApiClient] Calling API: {url}");

        using var request = UnityWebRequest.Get(url);
        request.SetRequestHeader("X-Unity-API-Key", unityClientKey);
        request.timeout = timeoutSeconds;
        yield return request.SendWebRequest();

        if (request.result != UnityWebRequest.Result.Success)
        {
            var body = request.downloadHandler != null ? request.downloadHandler.text : string.Empty;
            var message = $"HTTP {(long)request.responseCode} - {request.error}. {body}";
            Debug.LogWarning($"[ARContentApiClient] API failed: {message}");
            onError?.Invoke(message);
            yield break;
        }

        try
        {
            var json = request.downloadHandler.text;
            var response = JsonUtility.FromJson<ARContentResponse>(json);
            Debug.Log($"[ARContentApiClient] API success: {response.markerCode} -> {response.arContentTitle}");
            onSuccess?.Invoke(response);
        }
        catch (Exception ex)
        {
            Debug.LogWarning($"[ARContentApiClient] Parse failed: {ex.Message}");
            onError?.Invoke(ex.Message);
        }
    }

    private string ResolveBackendBaseUrl()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        if (!string.IsNullOrWhiteSpace(androidBackendBaseUrl))
            return androidBackendBaseUrl;
#endif
        return backendBaseUrl;
    }
}
