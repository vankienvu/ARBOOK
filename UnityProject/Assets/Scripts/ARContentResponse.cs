using System;
using System.Collections.Generic;

[Serializable]
public class ARContentResponse
{
    public string markerCode;
    public string markerName;
    public long lessonId;
    public string lessonTitle;
    public long arContentId;
    public string arContentTitle;
    public string description;
    public string modelName;
    public string modelUrl;
    public List<string> animationNames = new();
    public List<ARLabelResponse> labels = new();
    public string status;
}

[Serializable]
public class ARLabelResponse
{
    public string name;
    public string description;
}
