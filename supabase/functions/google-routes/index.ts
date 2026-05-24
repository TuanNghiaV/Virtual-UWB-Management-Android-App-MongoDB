import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(payload: any, status: number = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ success: false, error: "Method not allowed", source: "GOOGLE_ROUTES" }, 405);
  }

  const apiKey = Deno.env.get("GOOGLE_ROUTES_API_KEY");
  if (!apiKey) {
    console.error("GOOGLE_ROUTES_API_KEY is not set.");
    return json({ success: false, error: "Internal Server Configuration Error", source: "GOOGLE_ROUTES" }, 500);
  }

  try {
    const { origin, destination, travelMode = "WALK" } = await req.json();

    if (!origin?.latitude || !origin?.longitude || !destination?.latitude || !destination?.longitude) {
      return json({ success: false, error: "Missing origin or destination coordinates.", source: "GOOGLE_ROUTES" }, 400);
    }

    const apiUrl = "https://routes.googleapis.com/directions/v2:computeRoutes";
    
    const requestBody = {
      origin: {
        location: {
          latLng: {
            latitude: origin.latitude,
            longitude: origin.longitude
          }
        }
      },
      destination: {
        location: {
          latLng: {
            latitude: destination.latitude,
            longitude: destination.longitude
          }
        }
      },
      travelMode: travelMode,
      computeAlternativeRoutes: false,
      languageCode: "vi-VN",
      units: "METRIC"
    };

    const fieldMask = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline,routes.legs.steps.distanceMeters,routes.legs.steps.staticDuration,routes.legs.steps.polyline.encodedPolyline,routes.legs.steps.navigationInstruction";

    const response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": apiKey,
        "X-Goog-FieldMask": fieldMask
      },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error("Google Routes API Error:", errorText);
      return json({ success: false, error: "Failed to compute route from Google API", source: "GOOGLE_ROUTES" }, response.status);
    }

    const data = await response.json();
    const route = data.routes?.[0];

    if (!route) {
      return json({ success: false, error: "No route found", source: "GOOGLE_ROUTES" }, 404);
    }

    const steps = route.legs?.[0]?.steps?.map((step: any) => ({
      distanceMeters: step.distanceMeters,
      duration: step.staticDuration,
      instruction: step.navigationInstruction?.instructions || "",
      encodedPolyline: step.polyline?.encodedPolyline
    })) || [];

    const result = {
      success: true,
      distanceMeters: route.distanceMeters,
      duration: route.duration,
      encodedPolyline: route.polyline?.encodedPolyline,
      steps: steps,
      source: "GOOGLE_ROUTES"
    };

    return json(result);

  } catch (err: any) {
    console.error("Exception in google-routes Edge Function:", err);
    return json({ success: false, error: err.message || "An unexpected error occurred.", source: "GOOGLE_ROUTES" }, 500);
  }
});
