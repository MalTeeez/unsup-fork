package com.unascribed.sup.build.transformer;

import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("com.grack.nanojson.JsonStringWriter")
public class JsonStringWriterTransformer extends JsonWriterImplTransformer {}
