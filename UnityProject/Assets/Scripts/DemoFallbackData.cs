using System.Collections.Generic;

public static class DemoFallbackData
{
    public static ARContentResponse Resolve(string markerCode)
    {
        return markerCode switch
        {
            "SOLAR_SYSTEM_001" => new ARContentResponse
            {
                markerCode = markerCode,
                markerName = "Marker hệ Mặt Trời",
                lessonId = 2,
                lessonTitle = "Hệ Mặt Trời",
                arContentId = 2,
                arContentTitle = "Mô hình hệ Mặt Trời 3D",
                description = "Dữ liệu fallback mô phỏng quỹ đạo cơ bản của các hành tinh.",
                modelName = "Solar System Placeholder",
                modelUrl = "local-placeholder",
                animationNames = new List<string> { "OrbitSimulation" },
                labels = new List<ARLabelResponse>
                {
                    new() { name = "Mặt Trời", description = "Nguồn sáng trung tâm." },
                    new() { name = "Quỹ đạo", description = "Đường chuyển động mô phỏng." }
                },
                status = "PUBLISHED"
            },
            "HEART_001" => new ARContentResponse
            {
                markerCode = markerCode,
                markerName = "Marker trái tim",
                lessonId = 3,
                lessonTitle = "Cấu tạo trái tim",
                arContentId = 3,
                arContentTitle = "Mô hình trái tim 3D",
                description = "Dữ liệu fallback mô phỏng cấu tạo và nhịp đập của trái tim.",
                modelName = "Heart Placeholder",
                modelUrl = "local-placeholder",
                animationNames = new List<string> { "BeatHeart" },
                labels = new List<ARLabelResponse>
                {
                    new() { name = "Tâm thất", description = "Buồng tim bơm máu." },
                    new() { name = "Tâm nhĩ", description = "Buồng tim nhận máu." }
                },
                status = "PUBLISHED"
            },
            _ => new ARContentResponse
            {
                markerCode = "BIO_CELL_001",
                markerName = "Marker cấu tạo tế bào",
                lessonId = 1,
                lessonTitle = "Cấu tạo tế bào",
                arContentId = 1,
                arContentTitle = "Mô hình tế bào 3D",
                description = "Dữ liệu fallback mô phỏng các thành phần chính của tế bào.",
                modelName = "Cell Placeholder",
                modelUrl = "local-placeholder",
                animationNames = new List<string> { "RotateCell", "ShowParts" },
                labels = new List<ARLabelResponse>
                {
                    new() { name = "Nhân tế bào", description = "Điều khiển hoạt động của tế bào." },
                    new() { name = "Ty thể", description = "Cung cấp năng lượng cho tế bào." }
                },
                status = "PUBLISHED"
            }
        };
    }
}
