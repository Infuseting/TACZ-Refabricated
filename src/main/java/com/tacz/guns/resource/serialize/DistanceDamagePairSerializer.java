package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;

import java.lang.reflect.Type;

public class DistanceDamagePairSerializer implements JsonDeserializer<ExtraDamage.DistanceDamagePair> {
    private static final String INFINITE = "infinite";

    @Override
    public ExtraDamage.DistanceDamagePair deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected " + json + " to be a DistanceDamagePair because it's not an object");
        }
        JsonObject jsonObject = json.getAsJsonObject();
        if (!jsonObject.has("distance")) {
            throw new JsonSyntaxException("Expected " + json + " to be a DistanceDamagePair because it's not has distance field");
        }
        if (!jsonObject.has("damage") && !jsonObject.has("multiplier") && !jsonObject.has("damage_multiplier")) {
            throw new JsonSyntaxException("Expected " + json + " to be a DistanceDamagePair because it has neither damage nor multiplier field");
        }
        if (!jsonObject.get("distance").isJsonPrimitive()) {
            throw new JsonSyntaxException("Expected " + json + " to be a DistanceDamagePair because distance field is not a string or number");
        }
        float distance = 0;
        JsonPrimitive distPrimitive = jsonObject.get("distance").getAsJsonPrimitive();
        if (distPrimitive.isNumber()) {
            distance = distPrimitive.getAsFloat();
        } else if (distPrimitive.isString()) {
            if (INFINITE.equalsIgnoreCase(distPrimitive.getAsString())) {
                distance = Float.MAX_VALUE;
            } else {
                throw new JsonSyntaxException("Expected " + json + " to be a DistanceDamagePair because distance field is not '" + INFINITE + "'");
            }
        }

        Float multiplier = null;
        if (jsonObject.has("multiplier") && jsonObject.get("multiplier").isJsonPrimitive() && jsonObject.get("multiplier").getAsJsonPrimitive().isNumber()) {
            multiplier = jsonObject.get("multiplier").getAsFloat();
        } else if (jsonObject.has("damage_multiplier") && jsonObject.get("damage_multiplier").isJsonPrimitive() && jsonObject.get("damage_multiplier").getAsJsonPrimitive().isNumber()) {
            multiplier = jsonObject.get("damage_multiplier").getAsFloat();
        }

        float damage = 0f;
        if (jsonObject.has("damage") && jsonObject.get("damage").isJsonPrimitive() && jsonObject.get("damage").getAsJsonPrimitive().isNumber()) {
            damage = jsonObject.get("damage").getAsFloat();
        } else if (multiplier != null) {
            damage = multiplier;
        }

        return new ExtraDamage.DistanceDamagePair(distance, damage, multiplier);
    }
}
