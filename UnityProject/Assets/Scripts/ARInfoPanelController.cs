using UnityEngine;
using UnityEngine.UI;

public class ARInfoPanelController : MonoBehaviour
{
    [SerializeField] private Text markerCodeText;
    [SerializeField] private Text lessonTitleText;
    [SerializeField] private Text contentTitleText;
    [SerializeField] private Text descriptionText;
    [SerializeField] private Button playAnimationButton;
    [SerializeField] private Button resetButton;

    private ModelController activeModel;

    public void Configure(
        Text markerText,
        Text lessonText,
        Text contentText,
        Text detailsText,
        Button animationButton,
        Button modelResetButton)
    {
        markerCodeText = markerText;
        lessonTitleText = lessonText;
        contentTitleText = contentText;
        descriptionText = detailsText;
        playAnimationButton = animationButton;
        resetButton = modelResetButton;
    }

    private void Awake()
    {
        if (playAnimationButton != null)
            playAnimationButton.onClick.AddListener(() => activeModel?.PlayAnimation());
        if (resetButton != null)
            resetButton.onClick.AddListener(() => activeModel?.ResetModel());
    }

    public void Show(ARContentResponse content, ModelController modelController)
    {
        activeModel = modelController;
        if (markerCodeText != null) markerCodeText.text = content.markerCode;
        if (lessonTitleText != null) lessonTitleText.text = content.lessonTitle;
        if (contentTitleText != null) contentTitleText.text = content.arContentTitle;
        if (descriptionText != null) descriptionText.text = content.description;
        gameObject.SetActive(true);
        Debug.Log($"[ARInfoPanel] Show content: {content.markerCode}");
    }

    public void Hide()
    {
        gameObject.SetActive(false);
        activeModel = null;
    }
}
