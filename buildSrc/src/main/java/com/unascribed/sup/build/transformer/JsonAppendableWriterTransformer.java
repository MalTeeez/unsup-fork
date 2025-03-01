package com.unascribed.sup.build.transformer;

import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("com.grack.nanojson.JsonAppendableWriter")
public class JsonAppendableWriterTransformer extends JsonWriterImplTransformer {}
