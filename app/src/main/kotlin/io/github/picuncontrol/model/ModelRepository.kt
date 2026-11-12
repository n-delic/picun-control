package io.github.picuncontrol.model

import kotlinx.serialization.json.Json

/** In-memory lookup over the bundled model catalogue. */
class ModelRepository private constructor(val models: List<PicunModel>) {
  private val byProduct: Map<Pair<String, String>, PicunModel> = buildMap {
    for (model in models) {
      if (model.deviceModel.isBlank() || model.deviceNo.isBlank()) continue
      put(model.deviceModel.uppercase() to model.deviceNo.uppercase(), model)
    }
  }

  private val byBluetoothName: Map<String, PicunModel> = buildMap {
    for (model in models) {
      if (model.bluetoothName.isNotBlank()) putIfAbsent(normalize(model.bluetoothName), model)
    }
  }

  private val byName: Map<String, PicunModel> = buildMap {
    for (model in models) {
      if (model.name.isNotBlank()) putIfAbsent(normalize(model.name), model)
    }
  }

  fun findByProduct(productType: String?, productNumber: String?): PicunModel? {
    if (productType.isNullOrBlank() || productNumber.isNullOrBlank()) return null
    return byProduct[productType.uppercase() to productNumber.uppercase()]
  }

  /** Fallback for devices whose advertisement we could not decode. */
  fun findByBluetoothName(advertisedName: String?): PicunModel? {
    if (advertisedName.isNullOrBlank()) return null
    val key = normalize(advertisedName)
    byBluetoothName[key]?.let { return it }
    byName[key]?.let { return it }
    // Advertised names sometimes gain a suffix ("picun F2 LE"), so try prefix.
    return models.firstOrNull {
      it.bluetoothName.isNotBlank() && normalize(it.bluetoothName).let { n ->
        key.startsWith(n) || n.startsWith(key)
      }
    }
  }

  fun findById(id: Int): PicunModel? = models.firstOrNull { it.id == id }

  private fun normalize(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")

  companion object {
    private val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
    }

    fun parse(text: String): ModelRepository =
      ModelRepository(json.decodeFromString<List<ModelJson>>(text).map { it.toModel() })

    fun empty(): ModelRepository = ModelRepository(emptyList())
  }
}
