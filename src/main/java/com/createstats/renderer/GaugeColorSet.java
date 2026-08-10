package com.createstats.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
record GaugeColorSet(int bezelHighlight, int bezelMid, int bezelShadow, int bezelRivet, int dialFace, int tickMajor, int tickMinor, int numberLabel, int unitLabel, int needleBody, int needleBorder, int needleCounterWeight, int pivotCenter, int pivotHighlight, int zoneGreen, int zoneYellow, int zoneRed, int glassReflection, int glassEdgeTint) {}
