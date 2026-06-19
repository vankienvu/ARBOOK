using UnityEngine;

public static class PlaceholderModelFactory
{
    public static GameObject Create(string markerCode)
    {
        return markerCode switch
        {
            "SOLAR_SYSTEM_001" => CreateSolarSystem(),
            "HEART_001" => CreateHeart(),
            _ => CreateCell()
        };
    }

    private static GameObject CreateCell()
    {
        var root = new GameObject("Cell Placeholder Model");
        var membrane = Primitive(PrimitiveType.Sphere, "Cell Membrane", root.transform, new Vector3(0, 0.08f, 0), new Vector3(0.75f, 0.45f, 0.75f), new Color(0.2f, 0.7f, 0.9f, 0.45f));
        var nucleus = Primitive(PrimitiveType.Sphere, "Nucleus", root.transform, new Vector3(0.12f, 0.16f, 0), Vector3.one * 0.22f, new Color(0.58f, 0.25f, 0.85f));
        var mitochondria = Primitive(PrimitiveType.Capsule, "Mitochondria", root.transform, new Vector3(-0.25f, 0.11f, 0.18f), Vector3.one * 0.14f, new Color(1f, 0.55f, 0.16f));
        membrane.name = "Cell Membrane";
        nucleus.name = "Nucleus";
        mitochondria.name = "Mitochondria";
        return root;
    }

    private static GameObject CreateSolarSystem()
    {
        var root = new GameObject("Solar System Placeholder Model");
        Primitive(PrimitiveType.Sphere, "Sun", root.transform, Vector3.up * 0.08f, Vector3.one * 0.2f, new Color(1f, 0.76f, 0.16f));
        for (int i = 0; i < 4; i++)
        {
            var angle = i * Mathf.PI * 0.5f;
            var radius = 0.35f + i * 0.16f;
            var pos = new Vector3(Mathf.Cos(angle) * radius, 0.08f, Mathf.Sin(angle) * radius);
            Primitive(PrimitiveType.Sphere, $"Planet {i + 1}", root.transform, pos, Vector3.one * (0.08f + i * 0.015f), Color.Lerp(Color.cyan, Color.red, i / 4f));
        }
        return root;
    }

    private static GameObject CreateHeart()
    {
        var root = new GameObject("Heart Placeholder Model");
        Primitive(PrimitiveType.Sphere, "Left Chamber", root.transform, new Vector3(-0.11f, 0.12f, 0), new Vector3(0.28f, 0.38f, 0.22f), new Color(0.86f, 0.08f, 0.18f));
        Primitive(PrimitiveType.Sphere, "Right Chamber", root.transform, new Vector3(0.11f, 0.12f, 0), new Vector3(0.28f, 0.38f, 0.22f), new Color(0.92f, 0.14f, 0.24f));
        Primitive(PrimitiveType.Cylinder, "Aorta", root.transform, new Vector3(0, 0.45f, 0), new Vector3(0.12f, 0.28f, 0.12f), new Color(0.7f, 0.02f, 0.12f));
        return root;
    }

    private static GameObject Primitive(PrimitiveType type, string name, Transform parent, Vector3 position, Vector3 scale, Color color)
    {
        var go = GameObject.CreatePrimitive(type);
        go.name = name;
        go.transform.SetParent(parent, false);
        go.transform.localPosition = position;
        go.transform.localScale = scale;
        var renderer = go.GetComponent<Renderer>();
        renderer.material = CreateMaterial(color);
        return go;
    }

    private static Material CreateMaterial(Color color)
    {
        var material = new Material(Shader.Find("Standard")) { color = color };
        if (color.a >= 0.99f) return material;

        material.SetFloat("_Mode", 3f);
        material.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.SrcAlpha);
        material.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
        material.SetInt("_ZWrite", 0);
        material.DisableKeyword("_ALPHATEST_ON");
        material.EnableKeyword("_ALPHABLEND_ON");
        material.DisableKeyword("_ALPHAPREMULTIPLY_ON");
        material.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
        return material;
    }
}
