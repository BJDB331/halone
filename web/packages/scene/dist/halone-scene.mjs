import {
  Unit_instance3vdlo4e4f5ggx as Unit_instance,
  Enum3alwj03lh1n41 as Enum,
  protoOf180f3jzyo7rfj as protoOf,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  VOID3gxj6tk5isa35 as VOID,
  getNumberHashCode2l4nbdcihl25f as getNumberHashCode,
  equals2au1ep9vhcato as equals,
  toString1pkumu07cwy4m as toString,
  hashCodeq5arwsb9dgti as hashCode,
  coerceAtMost1wyvl5p1shiw0 as coerceAtMost,
  addAll1k27qatfgp3k5 as addAll,
  sortedWith2csnbbb21k0lg as sortedWith,
  emptySetcxexqki71qfa as emptySet,
  HashSet_init_$Create$1nt0scwlu5nu0 as HashSet_init_$Create$,
  ArrayList_init_$Create$1z3aa4voswqq as ArrayList_init_$Create$,
  compareTo3ankvs086tmwq as compareTo,
  NoSuchElementException_init_$Create$3v2cvqwdgxfkg as NoSuchElementException_init_$Create$,
  to2cs3ny02qtbcb as to,
  listOf1jh22dvmctj1r as listOf,
  plus310ted5e4i90h as plus,
  collectionSizeOrDefault36dulx8yinfqm as collectionSizeOrDefault,
  ArrayList_init_$Create$3o344dm0ee4ws as ArrayList_init_$Create$_0,
  listOfNotNull2woi2boe01ub4 as listOfNotNull,
  Collection1k04j3hzsbod0 as Collection,
  isInterface3d6p8outrmvmk as isInterface,
  coerceInlzh524ulyz3c as coerceIn,
  initMetadataForCompanion1wyw17z38v6ac as initMetadataForCompanion,
  FunctionAdapter3lcrrz3moet5b as FunctionAdapter,
  Comparator2b3maoeh98xtg as Comparator,
  startsWith26w8qjqapeeq6 as startsWith,
  endsWith3cq61xxngobwh as endsWith,
  compareValues1n2ayl87ihzfk as compareValues,
  coerceAtLeast1b3m6f7g22ivr as coerceAtLeast,
  HashMap_init_$Create$34xhy9w7sl519 as HashMap_init_$Create$,
  removeAll3cm9rh0qak2y6 as removeAll,
  firstOrNull1982767dljvdy as firstOrNull,
  checkIndexOverflow3frtmheghr0th as checkIndexOverflow,
  StringBuilder_init_$Create$3pb07k1b5jr7z as StringBuilder_init_$Create$,
  padEnd2jv3jyj2267cs as padEnd,
  compareBy2697bq0ffft96 as compareBy,
  asReversed308kw52j6ls1u as asReversed,
  THROW_IAE23kobfj9wdoxr as THROW_IAE,
  enumEntries20mr21zbe3az4 as enumEntries,
  NoSuchElementException_init_$Create$2e8uxak5my80n as NoSuchElementException_init_$Create$_0,
  initMetadataForObject1cxne3s9w65el as initMetadataForObject,
  getBooleanHashCode1bbj3u6b3v0a7 as getBooleanHashCode,
  Long2qws0ah9gnpki as Long,
  fromInt1lka3ktyu79a4 as fromInt,
  multiply18i3gv3wlmcjg as multiply,
  toNumberlmbpvqo27r53 as toNumber,
  roundToLong2s902lrwaad4n as roundToLong,
  divide3tol6kxdi8xn6 as divide,
  modulo3mmbfwxzpcw3a as modulo,
  _Char___init__impl__6a9atx2yltdocdrxs4d as _Char___init__impl__6a9atx,
  padStart36w1507hs626a as padStart,
  equalsLong28bsrfhwvd686 as equalsLong,
  joinToString1cxrrlmo0chqs as joinToString,
  IllegalArgumentException_init_$Create$1mgk48flhjim3 as IllegalArgumentException_init_$Create$,
  copyToArray2j022khrow2yi as copyToArray,
  defineProp3ur6h3slcvq4x as defineProp,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
var imul = Math.imul;
var sign = Math.sign;
//endregion
//region block: pre-declaration
initMetadataForClass(CollisionPolicy, 'CollisionPolicy', VOID, Enum);
initMetadataForClass(DockVisibility, 'DockVisibility', VOID, Enum);
initMetadataForClass(PanelHit, 'PanelHit');
initMetadataForClass(PanelSceneHit, 'PanelSceneHit');
initMetadataForClass(ResolvedTarget, 'ResolvedTarget');
initMetadataForClass(ResizeAnimation, 'ResizeAnimation');
initMetadataForClass(Aim, 'Aim');
initMetadataForCompanion(Companion);
initMetadataForClass(sam$kotlin_Comparator$0, 'sam$kotlin_Comparator$0', VOID, VOID, [Comparator, FunctionAdapter]);
initMetadataForClass(sam$kotlin_Comparator$0_0, 'sam$kotlin_Comparator$0', VOID, VOID, [Comparator, FunctionAdapter]);
initMetadataForClass(PanelScene, 'PanelScene', PanelScene);
initMetadataForClass(Corner, 'Corner', VOID, Enum);
initMetadataForObject(ResizeHandles, 'ResizeHandles');
initMetadataForClass(PanelPose, 'PanelPose', PanelPose);
initMetadataForCompanion(Companion_0);
initMetadataForClass(SmoothFollow, 'SmoothFollow', SmoothFollow);
initMetadataForCompanion(Companion_1);
initMetadataForClass(ScenePanel, 'ScenePanel');
initMetadataForCompanion(Companion_2);
initMetadataForClass(HitPart, 'HitPart', VOID, Enum);
initMetadataForClass(AngularRect, 'AngularRect');
initMetadataForClass(PanelRect, 'PanelRect');
initMetadataForObject(TitleBar, 'TitleBar');
initMetadataForCompanion(Companion_3);
initMetadataForClass(Vec3, 'Vec3');
initMetadataForClass(OverlayState, 'OverlayState');
initMetadataForClass(PanelState, 'PanelState');
initMetadataForCompanion(Companion_4);
initMetadataForClass(SimulatorScene, 'SimulatorScene');
initMetadataForClass(SimulatorPanel, 'SimulatorPanel', VOID, ScenePanel);
//endregion
var CollisionPolicy_OVERLAP_instance;
var CollisionPolicy_AVOID_PINNED_instance;
var CollisionPolicy_AVOID_ALL_instance;
var CollisionPolicy_entriesInitialized;
function CollisionPolicy_initEntries() {
  if (CollisionPolicy_entriesInitialized)
    return Unit_instance;
  CollisionPolicy_entriesInitialized = true;
  CollisionPolicy_OVERLAP_instance = new CollisionPolicy('OVERLAP', 0);
  CollisionPolicy_AVOID_PINNED_instance = new CollisionPolicy('AVOID_PINNED', 1);
  CollisionPolicy_AVOID_ALL_instance = new CollisionPolicy('AVOID_ALL', 2);
}
function CollisionPolicy(name, ordinal) {
  Enum.call(this, name, ordinal);
}
var DockVisibility_ALWAYS_instance;
var DockVisibility_AUTO_HIDE_instance;
var DockVisibility_entriesInitialized;
function DockVisibility_initEntries() {
  if (DockVisibility_entriesInitialized)
    return Unit_instance;
  DockVisibility_entriesInitialized = true;
  DockVisibility_ALWAYS_instance = new DockVisibility('ALWAYS', 0);
  DockVisibility_AUTO_HIDE_instance = new DockVisibility('AUTO_HIDE', 1);
}
function DockVisibility(name, ordinal) {
  Enum.call(this, name, ordinal);
}
function PanelHit(x, y) {
  this.s6_1 = x;
  this.t6_1 = y;
}
protoOf(PanelHit).toString = function () {
  return 'PanelHit(x=' + this.s6_1 + ', y=' + this.t6_1 + ')';
};
protoOf(PanelHit).hashCode = function () {
  var result = getNumberHashCode(this.s6_1);
  result = imul(result, 31) + getNumberHashCode(this.t6_1) | 0;
  return result;
};
protoOf(PanelHit).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PanelHit))
    return false;
  if (!equals(this.s6_1, other.s6_1))
    return false;
  if (!equals(this.t6_1, other.t6_1))
    return false;
  return true;
};
function PanelSceneHit(panel, hit, distanceMetres, part) {
  part = part === VOID ? HitPart_CONTENT_getInstance() : part;
  this.u6_1 = panel;
  this.v6_1 = hit;
  this.w6_1 = distanceMetres;
  this.x6_1 = part;
}
protoOf(PanelSceneHit).toString = function () {
  return 'PanelSceneHit(panel=' + toString(this.u6_1) + ', hit=' + this.v6_1.toString() + ', distanceMetres=' + this.w6_1 + ', part=' + this.x6_1.toString() + ')';
};
protoOf(PanelSceneHit).hashCode = function () {
  var result = hashCode(this.u6_1);
  result = imul(result, 31) + this.v6_1.hashCode() | 0;
  result = imul(result, 31) + getNumberHashCode(this.w6_1) | 0;
  result = imul(result, 31) + this.x6_1.hashCode() | 0;
  return result;
};
protoOf(PanelSceneHit).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PanelSceneHit))
    return false;
  if (!equals(this.u6_1, other.u6_1))
    return false;
  if (!this.v6_1.equals(other.v6_1))
    return false;
  if (!equals(this.w6_1, other.w6_1))
    return false;
  if (!this.x6_1.equals(other.x6_1))
    return false;
  return true;
};
function retargetOnHead($this, headYawRadians) {
  var tmp0_elvis_lhs = bandExcessAround($this, $this.l7_1, headYawRadians);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var excessFromOrigin = tmp;
  var tmp_0 = $this;
  var tmp_1;
  if ($this.n7_1) {
    tmp_1 = excessFromOrigin <= 0.0;
  } else {
    tmp_1 = excessFromOrigin < -Companion_getInstance().w9_1;
  }
  tmp_0.n7_1 = tmp_1;
  if ($this.n7_1)
    return Unit_instance;
  $this.k7_1 = headYawRadians;
  var tmp1_elvis_lhs = bandExcessAround($this, $this.j7_1, headYawRadians);
  var tmp_2;
  if (tmp1_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp_2 = tmp1_elvis_lhs;
  }
  var ahead = tmp_2;
  if ($this.u7_1 && ahead > widestPanelRadians($this))
    $this.e7_1 = true;
}
function _get_focusHeld__vtgs78($this) {
  return $this.d7_1 && !$this.f7_1 && ($this.e7_1 || $this.f8_1);
}
function targetOf($this, panel, headYawRadians) {
  return $this.f7_1 ? panel.ea_1.ma_1 : Companion_getInstance_1().la($this.j7_1 + panel.ga_1);
}
function advanceReturn($this, headYawRadians, dt) {
  var anchorMoved = false;
  if (!$this.f7_1 && dt > 0.0 && dt <= 0.5) {
    var target = $this.n7_1 ? $this.l7_1 : Companion_getInstance_1().la($this.k7_1 + $this.i7_1);
    var error = Companion_getInstance_1().la(target - $this.j7_1);
    // Inline function 'kotlin.math.abs' call
    if (Math.abs(error) > Companion_getInstance().q9_1) {
      var seconds = $this.e7_1 ? $this.p7_1 : $this.o7_1.ta_1;
      var perSecond = $this.e7_1 ? $this.q7_1 : $this.o7_1.ua_1;
      var step = error * coerceAtMost(dt / seconds, 1.0);
      var floor = toRadians(perSecond) * dt;
      // Inline function 'kotlin.math.abs' call
      var x = step;
      if (Math.abs(x) < floor) {
        // Inline function 'kotlin.math.sign' call
        var tmp = sign(error);
        // Inline function 'kotlin.math.abs' call
        // Inline function 'kotlin.math.min' call
        var b = Math.abs(error);
        step = tmp * Math.min(floor, b);
      }
      $this.j7_1 = Companion_getInstance_1().la($this.j7_1 + step);
      anchorMoved = true;
    }
  }
  var moved = false;
  var crossing = false;
  if ($this.f7_1) {
    var tmp0 = $this.g7_1;
    // Inline function 'kotlin.collections.plusAssign' call
    var elements = overlapping($this);
    addAll(tmp0, elements);
  }
  // Inline function 'kotlin.collections.sortedBy' call
  var this_0 = $this.y6_1;
  // Inline function 'kotlin.comparisons.compareBy' call
  var tmp_0 = PanelScene$advanceReturn$lambda;
  var tmp$ret$7 = new sam$kotlin_Comparator$0(tmp_0);
  var _iterator__ex2g4s = sortedWith(this_0, tmp$ret$7).g();
  $l$loop_1: while (_iterator__ex2g4s.h()) {
    var panel = _iterator__ex2g4s.i();
    if ($this.f7_1 && !$this.g7_1.q(panel.ba_1)) {
      var tmp0_0 = $this.a7_1;
      var tmp2 = panel.ba_1;
      // Inline function 'kotlin.collections.set' call
      var value = panel.ea_1.ma_1;
      tmp0_0.k2(tmp2, value);
      continue $l$loop_1;
    }
    var wanted = targetOf($this, panel, headYawRadians);
    var resolved = resolveTarget($this, panel, wanted, headYawRadians, dt);
    var tmp0_1 = $this.a7_1;
    var tmp2_0 = panel.ba_1;
    // Inline function 'kotlin.collections.set' call
    var value_0 = resolved.za_1;
    tmp0_1.k2(tmp2_0, value_0);
    if (resolved.ab_1)
      crossing = true;
    var timeConstant = resolved.ab_1 ? panel.fa_1.va_1 : $this.e7_1 ? $this.p7_1 : $this.t7_1 ? 0.1 : panel.fa_1.ta_1;
    var floor_0 = resolved.ab_1 ? panel.fa_1.wa_1 : $this.e7_1 ? $this.q7_1 : 0.0;
    var tmp0_elvis_lhs = panel.bb(resolved.za_1, dt, timeConstant, floor_0, true);
    var tmp_1;
    if (tmp0_elvis_lhs == null) {
      continue $l$loop_1;
    } else {
      tmp_1 = tmp0_elvis_lhs;
    }
    var next = tmp_1;
    if (next === panel.ea_1.ma_1)
      continue $l$loop_1;
    panel.ea_1 = panel.ea_1.cb(next);
    moved = true;
  }
  $this.f8_1 = crossing;
  $this.h7_1.z2();
  // Inline function 'kotlin.collections.forEach' call
  var _iterator__ex2g4s_0 = $this.y6_1.g();
  while (_iterator__ex2g4s_0.h()) {
    var element = _iterator__ex2g4s_0.i();
    var tmp0_2 = $this.h7_1;
    var tmp2_1 = element.ba_1;
    // Inline function 'kotlin.collections.set' call
    var value_1 = element.ea_1.ma_1;
    tmp0_2.k2(tmp2_1, value_1);
  }
  if (!moved && !anchorMoved && !correctCentring($this)) {
    $this.d7_1 = false;
    $this.e7_1 = false;
    $this.f7_1 = false;
    $this.g7_1.z2();
    $this.m7_1 = false;
    $this.n7_1 = false;
  }
  return moved || anchorMoved;
}
function hasUnresolvedOverlap($this) {
  // Inline function 'kotlin.collections.isNotEmpty' call
  return !overlapping($this).k();
}
function overlapping($this) {
  if ($this.b7_1.equals(CollisionPolicy_OVERLAP_getInstance()))
    return emptySet();
  var found = HashSet_init_$Create$();
  var _iterator__ex2g4s = $this.y6_1.g();
  $l$loop: while (_iterator__ex2g4s.h()) {
    var panel = _iterator__ex2g4s.i();
    if (!panel.fa_1.ra_1 || panel === $this.a8_1)
      continue $l$loop;
    var _iterator__ex2g4s_0 = $this.y6_1.g();
    $l$loop_2: while (_iterator__ex2g4s_0.h()) {
      var other = _iterator__ex2g4s_0.i();
      if (other === panel || other === $this.a8_1)
        continue $l$loop_2;
      if (!yieldsTo($this, panel, other))
        continue $l$loop_2;
      // Inline function 'kotlin.math.abs' call
      var x = panel.ea_1.na_1 - other.ea_1.na_1;
      var pitchGap = Math.abs(x);
      if (pitchGap > panel.db() + other.db()) {
        continue $l$loop_2;
      }
      // Inline function 'kotlin.math.abs' call
      var x_0 = Companion_getInstance_1().la(panel.ea_1.ma_1 - other.ea_1.ma_1);
      var separation = Math.abs(x_0);
      if (separation < panel.eb() + other.eb()) {
        // Inline function 'kotlin.collections.plusAssign' call
        var element = panel.ba_1;
        found.e(element);
      }
    }
  }
  return found;
}
function degrees($this, radians, positivePrefix) {
  positivePrefix = positivePrefix === VOID ? '' : positivePrefix;
  return formatFixed(toDegrees(radians), 0, positivePrefix);
}
function yieldsTo($this, panel, other) {
  return !other.fa_1.ra_1 ? true : $this.b7_1.equals(CollisionPolicy_AVOID_PINNED_getInstance()) ? false : panel.ha_1 ? false : true;
}
function nearestFollowerTo($this, headYawRadians) {
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = $this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.fa_1.ra_1 && !element.ha_1) {
      destination.e(element);
    }
  }
  var tmp$ret$3;
  $l$block_0: {
    // Inline function 'kotlin.collections.minByOrNull' call
    var iterator = destination.g();
    if (!iterator.h()) {
      tmp$ret$3 = null;
      break $l$block_0;
    }
    var minElem = iterator.i();
    if (!iterator.h()) {
      tmp$ret$3 = minElem;
      break $l$block_0;
    }
    var it = minElem;
    // Inline function 'kotlin.math.abs' call
    var x = Companion_getInstance_1().la($this.j7_1 + it.ga_1 - headYawRadians);
    var minValue = Math.abs(x);
    do {
      var e = iterator.i();
      // Inline function 'kotlin.math.abs' call
      var x_0 = Companion_getInstance_1().la($this.j7_1 + e.ga_1 - headYawRadians);
      var v = Math.abs(x_0);
      if (compareTo(minValue, v) > 0) {
        minElem = e;
        minValue = v;
      }
    }
     while (iterator.h());
    tmp$ret$3 = minElem;
  }
  return tmp$ret$3;
}
function widestPanelRadians($this) {
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = $this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.fa_1.ra_1) {
      destination.e(element);
    }
  }
  var tmp$ret$3;
  $l$block: {
    // Inline function 'kotlin.collections.maxOfOrNull' call
    var iterator = destination.g();
    if (!iterator.h()) {
      tmp$ret$3 = null;
      break $l$block;
    }
    var maxValue = 2.0 * iterator.i().eb();
    while (iterator.h()) {
      var v = 2.0 * iterator.i().eb();
      // Inline function 'kotlin.comparisons.maxOf' call
      var a = maxValue;
      maxValue = Math.max(a, v);
    }
    tmp$ret$3 = maxValue;
  }
  var tmp0_elvis_lhs = tmp$ret$3;
  return tmp0_elvis_lhs == null ? 0.0 : tmp0_elvis_lhs;
}
function hasLeftTheBand($this, headYawRadians) {
  if (!$this.r7_1)
    return false;
  var tmp = $this;
  var tmp0_elvis_lhs = bandExcessAround($this, $this.j7_1, headYawRadians);
  var tmp_0;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp_0 = tmp0_elvis_lhs;
  }
  tmp.g8_1 = tmp_0;
  return $this.g8_1 > 0.0;
}
function bandExcessAround($this, anchor, headYawRadians) {
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = $this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.fa_1.ra_1) {
      destination.e(element);
    }
  }
  var followers = destination;
  if (followers.k())
    return null;
  var error = Companion_getInstance_1().la(headYawRadians - anchor);
  var margin = toRadians($this.s7_1);
  // Inline function 'kotlin.collections.maxOf' call
  var iterator = followers.g();
  if (!iterator.h())
    throw NoSuchElementException_init_$Create$();
  var it = iterator.i();
  var maxValue = it.ga_1 + it.eb();
  while (iterator.h()) {
    var it_0 = iterator.i();
    var v = it_0.ga_1 + it_0.eb();
    // Inline function 'kotlin.comparisons.maxOf' call
    var a = maxValue;
    maxValue = Math.max(a, v);
  }
  var leftEdge = maxValue;
  // Inline function 'kotlin.collections.minOf' call
  var iterator_0 = followers.g();
  if (!iterator_0.h())
    throw NoSuchElementException_init_$Create$();
  var it_1 = iterator_0.i();
  var minValue = it_1.ga_1 - it_1.eb();
  while (iterator_0.h()) {
    var it_2 = iterator_0.i();
    var v_0 = it_2.ga_1 - it_2.eb();
    // Inline function 'kotlin.comparisons.minOf' call
    var a_0 = minValue;
    minValue = Math.min(a_0, v_0);
  }
  var rightEdge = minValue;
  var tmp0_0 = error - (leftEdge + margin);
  // Inline function 'kotlin.comparisons.maxOf' call
  var b = rightEdge - margin - error;
  return Math.max(tmp0_0, b);
}
function ResolvedTarget(target, crossing) {
  this.za_1 = target;
  this.ab_1 = crossing;
}
function resolveTarget($this, panel, wanted, headYawRadians, dt) {
  if ($this.b7_1.equals(CollisionPolicy_OVERLAP_getInstance()))
    return new ResolvedTarget(wanted, false);
  var target = wanted;
  var crossing = false;
  var _iterator__ex2g4s = $this.y6_1.g();
  $l$loop_2: while (_iterator__ex2g4s.h()) {
    var other = _iterator__ex2g4s.i();
    if (other === panel)
      continue $l$loop_2;
    // Inline function 'kotlin.math.abs' call
    var x = panel.ea_1.na_1 - other.ea_1.na_1;
    var pitchGap = Math.abs(x);
    if (pitchGap > panel.db() + other.db())
      continue $l$loop_2;
    if (!yieldsTo($this, panel, other))
      continue $l$loop_2;
    if (other.fa_1.ra_1) {
      var separation = panel.eb() + other.eb() + Companion_getInstance().r9_1;
      var needed = separation + Companion_getInstance().y9_1;
      var tmp;
      if ($this.f7_1) {
        tmp = other.ea_1.ma_1;
      } else {
        var tmp0_elvis_lhs = $this.a7_1.w(other.ba_1);
        tmp = tmp0_elvis_lhs == null ? targetOf($this, other, headYawRadians) : tmp0_elvis_lhs;
      }
      var otherTarget = tmp;
      var order = Companion_getInstance_1().la(panel.ga_1 - other.ga_1);
      var goPositive = !(order === 0.0) ? order > 0.0 : compareTo(panel.ba_1, other.ba_1) > 0;
      var gap = Companion_getInstance_1().la(target - otherTarget);
      if (goPositive && gap < needed) {
        target = Companion_getInstance_1().la(otherTarget + needed);
      } else if (!goPositive && gap > -needed) {
        target = Companion_getInstance_1().la(otherTarget - needed);
      }
      continue $l$loop_2;
    }
    var separation_0 = panel.eb() + other.eb() + Companion_getInstance().r9_1;
    var here = Companion_getInstance_1().la(panel.ea_1.ma_1 - other.ea_1.ma_1);
    var there = Companion_getInstance_1().la(target - other.ea_1.ma_1);
    var gaze = Companion_getInstance_1().la(headYawRadians - other.ea_1.ma_1);
    var edge = other.eb();
    var key = panel.ba_1 + '\x00' + other.ba_1;
    var tmp1_elvis_lhs = $this.z6_1.w(key);
    var committed = tmp1_elvis_lhs == null ? there >= 0.0 : tmp1_elvis_lhs;
    // Inline function 'kotlin.math.abs' call
    var clear = Math.abs(here) >= separation_0;
    var arrived = clear && here >= 0.0 === committed;
    var reference = other.fa_1.ra_1 ? there : gaze;
    var tmp_0;
    if (!arrived) {
      tmp_0 = committed;
    } else if (committed) {
      tmp_0 = reference >= -edge;
    } else {
      tmp_0 = reference > edge;
    }
    var goPositive_0 = tmp_0;
    // Inline function 'kotlin.collections.set' call
    $this.z6_1.k2(key, goPositive_0);
    var targetSide = Companion_getInstance_1().la(target - other.ea_1.ma_1) >= 0.0;
    if (!clear || !(targetSide === here >= 0.0))
      crossing = true;
    var parking = separation_0 + (other.fa_1.ra_1 ? Companion_getInstance().y9_1 : Companion_getInstance().x9_1);
    if (goPositive_0 && there < parking) {
      target = Companion_getInstance_1().la(other.ea_1.ma_1 + parking);
    } else if (!goPositive_0 && there > -parking) {
      target = Companion_getInstance_1().la(other.ea_1.ma_1 - parking);
    }
  }
  // Inline function 'kotlin.collections.mapNotNull' call
  var tmp0 = $this.y6_1;
  // Inline function 'kotlin.collections.mapNotNullTo' call
  var destination = ArrayList_init_$Create$();
  // Inline function 'kotlin.collections.forEach' call
  var _iterator__ex2g4s_0 = tmp0.g();
  while (_iterator__ex2g4s_0.h()) {
    var element = _iterator__ex2g4s_0.i();
    var tmp$ret$7;
    $l$block_1: {
      if (element === panel || element === $this.a8_1) {
        tmp$ret$7 = null;
        break $l$block_1;
      }
      if (!yieldsTo($this, panel, element)) {
        tmp$ret$7 = null;
        break $l$block_1;
      }
      // Inline function 'kotlin.math.abs' call
      var x_0 = panel.ea_1.na_1 - element.ea_1.na_1;
      var pitchGap_0 = Math.abs(x_0);
      if (pitchGap_0 > panel.db() + element.db()) {
        tmp$ret$7 = null;
        break $l$block_1;
      }
      var separation_1 = panel.eb() + element.eb() + Companion_getInstance().r9_1;
      var tmp_1;
      if (element.fa_1.ra_1) {
        var tmp_2;
        if ($this.f7_1) {
          tmp_2 = element.ea_1.ma_1;
        } else {
          var tmp0_elvis_lhs_0 = $this.a7_1.w(element.ba_1);
          tmp_2 = tmp0_elvis_lhs_0 == null ? targetOf($this, element, headYawRadians) : tmp0_elvis_lhs_0;
        }
        tmp_1 = tmp_2;
      } else {
        tmp_1 = element.ea_1.ma_1;
      }
      var centre = tmp_1;
      var half = separation_1 + (element.fa_1.ra_1 ? Companion_getInstance().y9_1 : Companion_getInstance().x9_1);
      tmp$ret$7 = to(centre, half);
    }
    var tmp0_safe_receiver = tmp$ret$7;
    if (tmp0_safe_receiver == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      destination.e(tmp0_safe_receiver);
    }
  }
  var blocked = destination;
  if (!resolveTarget$feasible(blocked, target)) {
    // Inline function 'kotlin.collections.flatMap' call
    // Inline function 'kotlin.collections.flatMapTo' call
    var destination_0 = ArrayList_init_$Create$();
    var _iterator__ex2g4s_1 = blocked.g();
    while (_iterator__ex2g4s_1.h()) {
      var element_0 = _iterator__ex2g4s_1.i();
      var centre_0 = element_0.q6();
      var half_0 = element_0.r6();
      var list = listOf([Companion_getInstance_1().la(centre_0 + half_0), Companion_getInstance_1().la(centre_0 - half_0)]);
      addAll(destination_0, list);
    }
    // Inline function 'kotlin.collections.filter' call
    // Inline function 'kotlin.collections.filterTo' call
    var destination_1 = ArrayList_init_$Create$();
    var _iterator__ex2g4s_2 = destination_0.g();
    while (_iterator__ex2g4s_2.h()) {
      var element_1 = _iterator__ex2g4s_2.i();
      if (resolveTarget$feasible(blocked, element_1)) {
        destination_1.e(element_1);
      }
    }
    var tmp$ret$17;
    $l$block_3: {
      // Inline function 'kotlin.collections.minByOrNull' call
      var iterator = destination_1.g();
      if (!iterator.h()) {
        tmp$ret$17 = null;
        break $l$block_3;
      }
      var minElem = iterator.i();
      if (!iterator.h()) {
        tmp$ret$17 = minElem;
        break $l$block_3;
      }
      var it = minElem;
      // Inline function 'kotlin.math.abs' call
      var x_1 = Companion_getInstance_1().la(it - wanted);
      var minValue = Math.abs(x_1);
      do {
        var e = iterator.i();
        // Inline function 'kotlin.math.abs' call
        var x_2 = Companion_getInstance_1().la(e - wanted);
        var v = Math.abs(x_2);
        if (compareTo(minValue, v) > 0) {
          minElem = e;
          minValue = v;
        }
      }
       while (iterator.h());
      tmp$ret$17 = minElem;
    }
    var candidate = tmp$ret$17;
    if (!(candidate == null)) {
      // Inline function 'kotlin.math.abs' call
      var x_3 = Companion_getInstance_1().la(candidate - target);
      if (Math.abs(x_3) > Companion_getInstance().p9_1) {
        crossing = true;
      }
      target = candidate;
    }
  }
  var displaced = !(target === wanted) || ($this.f7_1 && $this.g7_1.q(panel.ba_1));
  if (!$this.b7_1.equals(CollisionPolicy_OVERLAP_getInstance()) && displaced) {
    // Inline function 'kotlin.collections.mapNotNull' call
    var tmp0_0 = $this.y6_1;
    // Inline function 'kotlin.collections.mapNotNullTo' call
    var destination_2 = ArrayList_init_$Create$();
    // Inline function 'kotlin.collections.forEach' call
    var _iterator__ex2g4s_3 = tmp0_0.g();
    while (_iterator__ex2g4s_3.h()) {
      var element_2 = _iterator__ex2g4s_3.i();
      var tmp$ret$27;
      $l$block_6: {
        if (element_2 === panel || element_2 === $this.a8_1 || !element_2.fa_1.ra_1) {
          tmp$ret$27 = null;
          break $l$block_6;
        }
        // Inline function 'kotlin.math.abs' call
        var x_4 = panel.ea_1.na_1 - element_2.ea_1.na_1;
        var pitchGap_1 = Math.abs(x_4);
        if (pitchGap_1 > panel.db() + element_2.db()) {
          tmp$ret$27 = null;
          break $l$block_6;
        }
        var centre_1 = element_2.ea_1.ma_1;
        var tmp0_elvis_lhs_1 = $this.h7_1.w(element_2.ba_1);
        var previous = tmp0_elvis_lhs_1 == null ? centre_1 : tmp0_elvis_lhs_1;
        // Inline function 'kotlin.math.abs' call
        var x_5 = Companion_getInstance_1().la(centre_1 - previous);
        if (Math.abs(x_5) > Companion_getInstance().z9_1) {
          tmp$ret$27 = null;
          break $l$block_6;
        }
        tmp$ret$27 = to(centre_1, panel.eb() + element_2.eb() + Companion_getInstance().r9_1 + Companion_getInstance().y9_1);
      }
      var tmp0_safe_receiver_0 = tmp$ret$27;
      if (tmp0_safe_receiver_0 == null)
        null;
      else {
        // Inline function 'kotlin.let' call
        destination_2.e(tmp0_safe_receiver_0);
      }
    }
    var occupied = plus(blocked, destination_2);
    if (!resolveTarget$free(occupied, target)) {
      // Inline function 'kotlin.collections.flatMap' call
      // Inline function 'kotlin.collections.flatMapTo' call
      var destination_3 = ArrayList_init_$Create$();
      var _iterator__ex2g4s_4 = occupied.g();
      while (_iterator__ex2g4s_4.h()) {
        var element_3 = _iterator__ex2g4s_4.i();
        var centre_2 = element_3.q6();
        var half_1 = element_3.r6();
        var list_0 = listOf([Companion_getInstance_1().la(centre_2 + half_1), Companion_getInstance_1().la(centre_2 - half_1)]);
        addAll(destination_3, list_0);
      }
      // Inline function 'kotlin.collections.filter' call
      // Inline function 'kotlin.collections.filterTo' call
      var destination_4 = ArrayList_init_$Create$();
      var _iterator__ex2g4s_5 = destination_3.g();
      while (_iterator__ex2g4s_5.h()) {
        var element_4 = _iterator__ex2g4s_5.i();
        if (resolveTarget$free(occupied, element_4)) {
          destination_4.e(element_4);
        }
      }
      var tmp$ret$38;
      $l$block_8: {
        // Inline function 'kotlin.collections.minByOrNull' call
        var iterator_0 = destination_4.g();
        if (!iterator_0.h()) {
          tmp$ret$38 = null;
          break $l$block_8;
        }
        var minElem_0 = iterator_0.i();
        if (!iterator_0.h()) {
          tmp$ret$38 = minElem_0;
          break $l$block_8;
        }
        var it_0 = minElem_0;
        // Inline function 'kotlin.math.abs' call
        var x_6 = Companion_getInstance_1().la(it_0 - wanted);
        var minValue_0 = Math.abs(x_6);
        do {
          var e_0 = iterator_0.i();
          // Inline function 'kotlin.math.abs' call
          var x_7 = Companion_getInstance_1().la(e_0 - wanted);
          var v_0 = Math.abs(x_7);
          if (compareTo(minValue_0, v_0) > 0) {
            minElem_0 = e_0;
            minValue_0 = v_0;
          }
        }
         while (iterator_0.h());
        tmp$ret$38 = minElem_0;
      }
      var tmp2_safe_receiver = tmp$ret$38;
      if (tmp2_safe_receiver == null)
        null;
      else {
        // Inline function 'kotlin.let' call
        crossing = true;
        target = tmp2_safe_receiver;
      }
    }
  }
  return new ResolvedTarget(target, crossing);
}
function windowReach($this) {
  // Inline function 'kotlin.collections.map' call
  var this_0 = $this.y6_1;
  // Inline function 'kotlin.collections.mapTo' call
  var destination = ArrayList_init_$Create$_0(collectionSizeOrDefault(this_0, 10));
  var _iterator__ex2g4s = this_0.g();
  while (_iterator__ex2g4s.h()) {
    var item = _iterator__ex2g4s.i();
    var pose = item.ea_1;
    var metresPerPx = pose.pa_1 / item.fb();
    var distance = pose.oa_1;
    var handles = 64.0;
    // Inline function 'kotlin.comparisons.maxOf' call
    var a = 14.0 + 56.0;
    var below = Math.max(a, handles);
    // Inline function 'kotlin.math.atan' call
    var x = (item.fb() / 2.0 + handles) * metresPerPx / distance;
    var tmp = Math.atan(x);
    // Inline function 'kotlin.math.atan' call
    var x_0 = (item.gb() / 2.0 + below) * metresPerPx / distance;
    var tmp$ret$5 = Math.atan(x_0);
    var tmp_0 = pose.na_1 - tmp$ret$5;
    // Inline function 'kotlin.math.atan' call
    var x_1 = (item.gb() / 2.0 + handles) * metresPerPx / distance;
    var tmp$ret$6 = Math.atan(x_1);
    var tmp$ret$2 = new AngularRect(pose.ma_1, tmp, tmp_0, pose.na_1 + tmp$ret$6);
    destination.e(tmp$ret$2);
  }
  return destination;
}
function ResizeAnimation(fromWidth, fromHeight, fromPitch, fromYaw, toWidth, toHeight, toPitch, toYaw, movesYaw) {
  this.hb_1 = fromWidth;
  this.ib_1 = fromHeight;
  this.jb_1 = fromPitch;
  this.kb_1 = fromYaw;
  this.lb_1 = toWidth;
  this.mb_1 = toHeight;
  this.nb_1 = toPitch;
  this.ob_1 = toYaw;
  this.pb_1 = movesYaw;
  this.qb_1 = 0.0;
}
function advanceResizeAnimations($this, dt) {
  if ($this.i8_1.k())
    return Unit_instance;
  // Inline function 'kotlin.collections.mutableListOf' call
  var done = ArrayList_init_$Create$();
  // Inline function 'kotlin.collections.iterator' call
  var _iterator__ex2g4s = $this.i8_1.x().g();
  while (_iterator__ex2g4s.h()) {
    var _destruct__k2r9zo = _iterator__ex2g4s.i();
    // Inline function 'kotlin.collections.component1' call
    var panel = _destruct__k2r9zo.t();
    // Inline function 'kotlin.collections.component2' call
    var animation = _destruct__k2r9zo.u();
    animation.qb_1 = coerceAtMost(animation.qb_1 + dt / 0.22, 1.0);
    // Inline function 'kotlin.let' call
    var it = 1.0 - animation.qb_1;
    var t = 1.0 - it * it * it;
    var tmp = panel;
    var tmp0_$this = panel.ea_1;
    var tmp1_widthMetres = advanceResizeAnimations$lerp(t, animation.hb_1, animation.lb_1);
    var tmp2_heightMetres = advanceResizeAnimations$lerp(t, animation.ib_1, animation.mb_1);
    var tmp3_pitchRadians = advanceResizeAnimations$lerp(t, animation.jb_1, animation.nb_1);
    var tmp_0;
    if (animation.pb_1) {
      tmp_0 = Companion_getInstance_1().la(animation.kb_1 + Companion_getInstance_1().la(animation.ob_1 - animation.kb_1) * t);
    } else {
      tmp_0 = panel.ea_1.ma_1;
    }
    var tmp4_yawRadians = tmp_0;
    tmp.ea_1 = tmp0_$this.cb(tmp4_yawRadians, tmp3_pitchRadians, VOID, tmp1_widthMetres, tmp2_heightMetres);
    if (animation.qb_1 >= 1.0) {
      // Inline function 'kotlin.collections.plusAssign' call
      done.e(panel);
    }
  }
  // Inline function 'kotlin.collections.forEach' call
  var _iterator__ex2g4s_0 = done.g();
  while (_iterator__ex2g4s_0.h()) {
    var element = _iterator__ex2g4s_0.i();
    $this.i8_1.b3(element);
  }
}
function placePopup($this) {
  var tmp0_elvis_lhs = $this.z8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var popup = tmp;
  var tmp1_elvis_lhs = $this.b9_1;
  var tmp_0;
  if (tmp1_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp_0 = tmp1_elvis_lhs;
  }
  var parent = tmp_0;
  if (!$this.y6_1.q(parent) && !(parent === $this.j8_1) && !(parent === $this.k8_1)) {
    $this.rb();
    return Unit_instance;
  }
  var pose = parent.ea_1;
  var metresPerPx = pose.pa_1 / parent.fb();
  var width = popup.fb();
  var height = popup.gb();
  var left = placePopup$fit($this.c9_1, width, parent.fb());
  var top = placePopup$fit($this.d9_1, height, parent.gb());
  var distance = pose.oa_1;
  var right = (left + width / 2.0 - parent.fb() / 2.0) * metresPerPx;
  var down = (top + height / 2.0 - parent.gb() / 2.0) * metresPerPx;
  var tmp_1 = popup;
  var tmp_2 = Companion_getInstance_1();
  // Inline function 'kotlin.math.atan' call
  var x = right / distance;
  var tmp$ret$0 = Math.atan(x);
  var tmp_3 = tmp_2.la(pose.ma_1 - tmp$ret$0);
  // Inline function 'kotlin.math.atan' call
  var x_0 = down / distance;
  var tmp$ret$1 = Math.atan(x_0);
  tmp_1.ea_1 = pose.cb(tmp_3, pose.na_1 - tmp$ret$1, VOID, width * metresPerPx, height * metresPerPx);
}
function correctCentring($this) {
  var tmp0_elvis_lhs = $this.e9_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var panel = tmp;
  if (!$this.y6_1.q(panel) || $this.g9_1 >= 2) {
    $this.e9_1 = null;
    return false;
  }
  var error = Companion_getInstance_1().la($this.f9_1 - panel.ea_1.ma_1);
  // Inline function 'kotlin.math.abs' call
  if (Math.abs(error) <= Companion_getInstance().h9_1) {
    $this.e9_1 = null;
    return false;
  }
  $this.g9_1 = $this.g9_1 + 1 | 0;
  $this.k7_1 = Companion_getInstance_1().la($this.k7_1 + $this.i7_1 + error);
  $this.i7_1 = 0.0;
  return true;
}
function advanceOverlays($this, headYawRadians, dt) {
  var tmp0_elvis_lhs = $this.j8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var dock = tmp;
  var previous = $this.w8_1;
  var yaw = previous == null || dt <= 0.0 ? headYawRadians : Companion_getInstance_1().la(previous + Companion_getInstance_1().la(headYawRadians - previous) * coerceAtMost(dt / 0.15, 1.0));
  $this.w8_1 = yaw;
  var tmp0 = windowReach($this);
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.minOfOrNull' call
    var iterator = tmp0.g();
    if (!iterator.h()) {
      tmp$ret$0 = null;
      break $l$block;
    }
    var minValue = iterator.i().ub_1;
    while (iterator.h()) {
      var v = iterator.i().ub_1;
      // Inline function 'kotlin.comparisons.minOf' call
      var a = minValue;
      minValue = Math.min(a, v);
    }
    tmp$ret$0 = minValue;
  }
  var windowsBottom = tmp$ret$0;
  var dockHalf = dock.db();
  var tmp1_safe_receiver = $this.k8_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.db();
  var launcherHalf = tmp2_elvis_lhs == null ? 0.0 : tmp2_elvis_lhs;
  var resting = -(launcherHalf + Companion_getInstance().n9_1 + dockHalf);
  var tmp_0;
  if (windowsBottom == null) {
    tmp_0 = resting;
  } else {
    // Inline function 'kotlin.comparisons.minOf' call
    var b = windowsBottom - Companion_getInstance().m9_1 - dockHalf;
    tmp_0 = Math.min(resting, b);
  }
  var dockPitch = tmp_0;
  dock.ea_1 = dock.ea_1.cb(yaw, dockPitch);
  var tmp3_safe_receiver = $this.k8_1;
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    tmp3_safe_receiver.ea_1 = tmp3_safe_receiver.ea_1.cb(yaw, dockPitch + dockHalf + Companion_getInstance().n9_1 + launcherHalf);
  }
  var tmp_1 = $this;
  var tmp_2;
  if (windowsBottom == null || $this.yb() || $this.l8_1.equals(DockVisibility_ALWAYS_getInstance())) {
    tmp_2 = true;
  } else if (!($this.a8_1 == null)) {
    tmp_2 = $this.n8_1;
  } else {
    var aim = $this.x8_1;
    var tmp_3;
    if (aim == null) {
      tmp_3 = null;
    } else {
      // Inline function 'kotlin.let' call
      var tmp0_safe_receiver = aimAngles($this, aim);
      tmp_3 = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.xb_1;
    }
    var pitches = listOfNotNull([tmp_3, $this.y8_1]);
    var tmp_4;
    if (!(aim == null) && !(intersect($this, dock, aim, false) == null)) {
      tmp_4 = true;
    } else {
      if (pitches.k()) {
        tmp_4 = $this.n8_1;
      } else {
        var tmp$ret$9;
        $l$block_1: {
          // Inline function 'kotlin.collections.any' call
          var tmp_5;
          if (isInterface(pitches, Collection)) {
            tmp_5 = pitches.k();
          } else {
            tmp_5 = false;
          }
          if (tmp_5) {
            tmp$ret$9 = false;
            break $l$block_1;
          }
          var _iterator__ex2g4s = pitches.g();
          while (_iterator__ex2g4s.h()) {
            var element = _iterator__ex2g4s.i();
            if (element < windowsBottom) {
              tmp$ret$9 = true;
              break $l$block_1;
            }
          }
          tmp$ret$9 = false;
        }
        if (tmp$ret$9) {
          tmp_4 = true;
        } else {
          var tmp$ret$11;
          $l$block_3: {
            // Inline function 'kotlin.collections.all' call
            var tmp_6;
            if (isInterface(pitches, Collection)) {
              tmp_6 = pitches.k();
            } else {
              tmp_6 = false;
            }
            if (tmp_6) {
              tmp$ret$11 = true;
              break $l$block_3;
            }
            var _iterator__ex2g4s_0 = pitches.g();
            while (_iterator__ex2g4s_0.h()) {
              var element_0 = _iterator__ex2g4s_0.i();
              if (!(element_0 > windowsBottom + Companion_getInstance().o9_1)) {
                tmp$ret$11 = false;
                break $l$block_3;
              }
            }
            tmp$ret$11 = true;
          }
          if (tmp$ret$11) {
            tmp_4 = false;
          } else {
            tmp_4 = $this.n8_1;
          }
        }
      }
    }
    tmp_2 = tmp_4;
  }
  tmp_1.n8_1 = tmp_2;
  var fade = dt > 0.0 ? dt / 0.15 : 1.0;
  $this.o8_1 = approachLinearly($this, $this.o8_1, $this.n8_1 ? 1.0 : 0.0, fade);
  $this.p8_1 = approachLinearly($this, $this.p8_1, $this.yb() ? 1.0 : 0.0, fade);
  advanceWindowsVisibility($this, dt, fade);
}
function advanceWindowsVisibility($this, dt, fade) {
  var aim = $this.x8_1;
  var held = !$this.q8_1 || !($this.a8_1 == null) || !($this.h8_1 == null) || $this.yb() || $this.a9_1;
  var tmp;
  if (!(aim == null)) {
    var tmp0 = $this.y6_1;
    var tmp$ret$0;
    $l$block_0: {
      // Inline function 'kotlin.collections.any' call
      var tmp_0;
      if (isInterface(tmp0, Collection)) {
        tmp_0 = tmp0.k();
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_0;
      }
      var _iterator__ex2g4s = tmp0.g();
      while (_iterator__ex2g4s.h()) {
        var element = _iterator__ex2g4s.i();
        if (!(intersect($this, element, aim) == null)) {
          tmp$ret$0 = true;
          break $l$block_0;
        }
      }
      tmp$ret$0 = false;
    }
    tmp = tmp$ret$0;
  } else {
    tmp = false;
  }
  var pointed = tmp;
  $this.v8_1 = held || pointed || aim == null ? 0.0 : $this.v8_1 + dt;
  $this.t8_1 = held || pointed || $this.v8_1 < $this.s8_1;
  $this.u8_1 = approachLinearly($this, $this.u8_1, $this.t8_1 ? 1.0 : 0.0, fade);
}
function approachLinearly($this, value, target, step) {
  var tmp;
  if (value < target) {
    // Inline function 'kotlin.comparisons.minOf' call
    var b = value + step;
    tmp = Math.min(target, b);
  } else {
    // Inline function 'kotlin.comparisons.maxOf' call
    var b_0 = value - step;
    tmp = Math.max(target, b_0);
  }
  return tmp;
}
function intersect($this, panel, direction, chrome) {
  chrome = chrome === VOID ? true : chrome;
  var pose = panel.ea_1;
  var local = Companion_getInstance().zb(direction, pose.ma_1, pose.na_1);
  if (local.cc_1 >= -1.0E-4)
    return null;
  var t = -pose.oa_1 / local.cc_1;
  if (t <= 0.0)
    return null;
  var u = local.ac_1 * t / pose.pa_1 + 0.5;
  var v = 0.5 - local.bc_1 * t / pose.qa_1;
  var x = u * panel.fb();
  var y = v * panel.gb();
  var tmp;
  if ((0.0 <= u ? u <= 1.0 : false) && (0.0 <= v ? v <= 1.0 : false)) {
    tmp = HitPart_CONTENT_getInstance();
  } else if (chrome) {
    var tmp0_elvis_lhs = TitleBar_getInstance().oc(panel.fb(), panel.gb(), x, y, panel.da_1, $this.r8_1);
    var tmp_0;
    if (tmp0_elvis_lhs == null) {
      var tmp1_safe_receiver = ResizeHandles_instance.vc(panel.fb(), panel.gb(), x, y);
      var tmp_1;
      if (tmp1_safe_receiver == null) {
        tmp_1 = null;
      } else {
        // Inline function 'kotlin.let' call
        tmp_1 = Companion_instance_2.wc(tmp1_safe_receiver);
      }
      tmp_0 = tmp_1;
    } else {
      tmp_0 = tmp0_elvis_lhs;
    }
    var tmp2_elvis_lhs = tmp_0;
    var tmp_2;
    if (tmp2_elvis_lhs == null) {
      return null;
    } else {
      tmp_2 = tmp2_elvis_lhs;
    }
    tmp = tmp_2;
  } else {
    return null;
  }
  var part = tmp;
  return new PanelSceneHit(panel, new PanelHit(x, y), t, part);
}
function Aim(yaw, pitch) {
  this.wb_1 = yaw;
  this.xb_1 = pitch;
}
function aimAngles($this, direction) {
  var length = direction.a();
  if (length < 1.0E-6)
    return null;
  var d = direction.xc(1.0 / length);
  var tmp0 = -d.ac_1;
  // Inline function 'kotlin.math.atan2' call
  var x = -d.cc_1;
  var tmp = Math.atan2(tmp0, x);
  // Inline function 'kotlin.math.asin' call
  var x_0 = coerceIn(d.bc_1, -1.0, 1.0);
  var tmp$ret$1 = Math.asin(x_0);
  return new Aim(tmp, tmp$ret$1);
}
function Companion() {
  Companion_instance = this;
  this.h9_1 = toRadians(1.0);
  this.i9_1 = 2;
  this.j9_1 = 0.22;
  this.k9_1 = 0.15;
  this.l9_1 = 0.15;
  this.m9_1 = toRadians(1.6);
  this.n9_1 = toRadians(0.8);
  this.o9_1 = toRadians(1.5);
  this.p9_1 = toRadians(0.01);
  this.q9_1 = toRadians(0.5);
  this.r9_1 = toRadians(2.0);
  this.s9_1 = 30.0;
  this.t9_1 = toRadians(6.0);
  this.u9_1 = 0.3;
  this.v9_1 = 0.1;
  this.w9_1 = toRadians(3.0);
  this.x9_1 = toRadians(6.0);
  this.y9_1 = toRadians(2.0);
  this.z9_1 = toRadians(0.05);
  this.aa_1 = toRadians(25.0);
}
protoOf(Companion).zb = function (direction, yawRadians, pitchRadians) {
  // Inline function 'kotlin.math.cos' call
  var cy = Math.cos(yawRadians);
  // Inline function 'kotlin.math.sin' call
  var sy = Math.sin(yawRadians);
  var x = cy * direction.ac_1 - sy * direction.cc_1;
  var zYaw = sy * direction.ac_1 + cy * direction.cc_1;
  if (pitchRadians === 0.0)
    return new Vec3(x, direction.bc_1, zYaw);
  // Inline function 'kotlin.math.cos' call
  var cp = Math.cos(pitchRadians);
  // Inline function 'kotlin.math.sin' call
  var sp = Math.sin(pitchRadians);
  return new Vec3(x, cp * direction.bc_1 + sp * zYaw, -sp * direction.bc_1 + cp * zYaw);
};
var Companion_instance;
function Companion_getInstance() {
  if (Companion_instance == null)
    new Companion();
  return Companion_instance;
}
function sam$kotlin_Comparator$0(function_0) {
  this.yc_1 = function_0;
}
protoOf(sam$kotlin_Comparator$0).l5 = function (a, b) {
  return this.yc_1(a, b);
};
protoOf(sam$kotlin_Comparator$0).compare = function (a, b) {
  return this.l5(a, b);
};
protoOf(sam$kotlin_Comparator$0).i1 = function () {
  return this.yc_1;
};
protoOf(sam$kotlin_Comparator$0).equals = function (other) {
  var tmp;
  if (!(other == null) ? isInterface(other, Comparator) : false) {
    var tmp_0;
    if (!(other == null) ? isInterface(other, FunctionAdapter) : false) {
      tmp_0 = equals(this.i1(), other.i1());
    } else {
      tmp_0 = false;
    }
    tmp = tmp_0;
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(sam$kotlin_Comparator$0).hashCode = function () {
  return hashCode(this.i1());
};
function sam$kotlin_Comparator$0_0(function_0) {
  this.zc_1 = function_0;
}
protoOf(sam$kotlin_Comparator$0_0).l5 = function (a, b) {
  return this.zc_1(a, b);
};
protoOf(sam$kotlin_Comparator$0_0).compare = function (a, b) {
  return this.l5(a, b);
};
protoOf(sam$kotlin_Comparator$0_0).i1 = function () {
  return this.zc_1;
};
protoOf(sam$kotlin_Comparator$0_0).equals = function (other) {
  var tmp;
  if (!(other == null) ? isInterface(other, Comparator) : false) {
    var tmp_0;
    if (!(other == null) ? isInterface(other, FunctionAdapter) : false) {
      tmp_0 = equals(this.i1(), other.i1());
    } else {
      tmp_0 = false;
    }
    tmp = tmp_0;
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(sam$kotlin_Comparator$0_0).hashCode = function () {
  return hashCode(this.i1());
};
function PanelScene$remove$lambda($panel) {
  return function (it) {
    return startsWith(it, $panel.ba_1 + '\x00') || endsWith(it, '\x00' + $panel.ba_1);
  };
}
function PanelScene$bringIntoView$lambda($panel, $nearest) {
  return function (it) {
    return it === $panel ? $nearest : it === $nearest ? $panel : it;
  };
}
function PanelScene$endGrab$lambda($panel) {
  return function (it) {
    return startsWith(it, $panel.ba_1 + '\x00');
  };
}
function PanelScene$advanceReturn$lambda(a, b) {
  // Inline function 'kotlin.comparisons.compareValuesBy' call
  var tmp = a.ga_1;
  var tmp$ret$2 = b.ga_1;
  return compareValues(tmp, tmp$ret$2);
}
function PanelScene$describe$lambda(a, b) {
  // Inline function 'kotlin.comparisons.compareValuesBy' call
  var tmp = a.ga_1;
  var tmp$ret$2 = b.ga_1;
  return compareValues(tmp, tmp$ret$2);
}
function resolveTarget$feasible(blocked, at) {
  var tmp$ret$0;
  $l$block_0: {
    // Inline function 'kotlin.collections.none' call
    var tmp;
    if (isInterface(blocked, Collection)) {
      tmp = blocked.k();
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_0;
    }
    var _iterator__ex2g4s = blocked.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      var centre = element.q6();
      var half = element.r6();
      // Inline function 'kotlin.math.abs' call
      var x = Companion_getInstance_1().la(at - centre);
      if (Math.abs(x) < half - Companion_getInstance().p9_1) {
        tmp$ret$0 = false;
        break $l$block_0;
      }
    }
    tmp$ret$0 = true;
  }
  return tmp$ret$0;
}
function resolveTarget$free(occupied, at) {
  var tmp$ret$0;
  $l$block_0: {
    // Inline function 'kotlin.collections.none' call
    var tmp;
    if (isInterface(occupied, Collection)) {
      tmp = occupied.k();
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_0;
    }
    var _iterator__ex2g4s = occupied.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      var centre = element.q6();
      var half = element.r6();
      // Inline function 'kotlin.math.abs' call
      var x = Companion_getInstance_1().la(at - centre);
      if (Math.abs(x) < half - Companion_getInstance().p9_1) {
        tmp$ret$0 = false;
        break $l$block_0;
      }
    }
    tmp$ret$0 = true;
  }
  return tmp$ret$0;
}
function resized$halfAngle(distance, metres) {
  // Inline function 'kotlin.math.atan' call
  var x = metres / 2.0 / distance;
  return Math.atan(x);
}
function advanceResizeAnimations$lerp(t, a, b) {
  return a + (b - a) * t;
}
function placePopup$fit(at, size, room) {
  return at + size <= room ? at : at - size >= 0.0 ? at - size : coerceAtLeast(room - size, 0.0);
}
function PanelScene$drawOrder$lambda(it) {
  return -it.ea_1.oa_1;
}
function PanelScene$drawOrder$lambda_0($held) {
  return function (it) {
    return it === $held;
  };
}
function PanelScene$drawOrder$lambda_1($focus) {
  return function (it) {
    return it === $focus;
  };
}
function PanelScene() {
  Companion_getInstance();
  this.y6_1 = concurrentPanelList();
  this.z6_1 = HashMap_init_$Create$();
  this.a7_1 = HashMap_init_$Create$();
  this.b7_1 = CollisionPolicy_AVOID_ALL_getInstance();
  this.c7_1 = 30.0;
  this.d7_1 = false;
  this.e7_1 = false;
  this.f7_1 = false;
  this.g7_1 = HashSet_init_$Create$();
  this.h7_1 = HashMap_init_$Create$();
  this.i7_1 = 0.0;
  this.j7_1 = 0.0;
  this.k7_1 = 0.0;
  this.l7_1 = 0.0;
  this.m7_1 = false;
  this.n7_1 = false;
  this.o7_1 = new SmoothFollow();
  this.p7_1 = 0.2;
  this.q7_1 = 150.0;
  this.r7_1 = true;
  this.s7_1 = 6.0;
  this.t7_1 = true;
  this.u7_1 = true;
  this.v7_1 = null;
  this.w7_1 = null;
  this.x7_1 = 0.0;
  this.y7_1 = null;
  this.z7_1 = 0.0;
  this.a8_1 = null;
  this.b8_1 = 0.0;
  this.c8_1 = 0.0;
  this.d8_1 = 0.0;
  this.e8_1 = 0.0;
  this.f8_1 = false;
  this.g8_1 = 0.0;
  this.h8_1 = null;
  this.i8_1 = HashMap_init_$Create$();
  this.j8_1 = null;
  this.k8_1 = null;
  this.l8_1 = DockVisibility_ALWAYS_getInstance();
  this.m8_1 = false;
  this.n8_1 = true;
  this.o8_1 = 1.0;
  this.p8_1 = 0.0;
  this.q8_1 = false;
  this.r8_1 = false;
  this.s8_1 = 1.0;
  this.t8_1 = true;
  this.u8_1 = 1.0;
  this.v8_1 = 0.0;
  this.w8_1 = null;
  this.x8_1 = null;
  this.y8_1 = null;
  this.z8_1 = null;
  this.a9_1 = false;
  this.b9_1 = null;
  this.c9_1 = 0.0;
  this.d9_1 = 0.0;
  this.e9_1 = null;
  this.f9_1 = 0.0;
  this.g9_1 = 0;
}
protoOf(PanelScene).ad = function () {
  return this.y6_1;
};
protoOf(PanelScene).bd = function () {
  return this.j7_1;
};
protoOf(PanelScene).cd = function () {
  return this.d7_1;
};
protoOf(PanelScene).dd = function (panel) {
  this.y6_1.e(panel);
  if (this.v7_1 == null)
    this.v7_1 = panel;
  return panel;
};
protoOf(PanelScene).ed = function (panel) {
  this.y6_1.k1(panel);
  this.i8_1.b3(panel);
  var tmp0_safe_receiver = this.h8_1;
  if ((tmp0_safe_receiver == null ? null : tmp0_safe_receiver.fd_1) === panel)
    this.h8_1 = null;
  this.a7_1.b3(panel.ba_1);
  if (this.a8_1 === panel)
    this.a8_1 = null;
  if (this.w7_1 === panel)
    this.w7_1 = null;
  var tmp = this.z6_1.j2();
  removeAll(tmp, PanelScene$remove$lambda(panel));
  if (this.v7_1 === panel)
    this.v7_1 = firstOrNull(this.y6_1);
};
protoOf(PanelScene).jd = function (id) {
  var tmp0 = this.y6_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (element.ba_1 === id) {
        tmp$ret$0 = element;
        break $l$block;
      }
    }
    tmp$ret$0 = null;
  }
  return tmp$ret$0;
};
protoOf(PanelScene).kd = function () {
  this.j7_1 = 0.0;
  this.k7_1 = 0.0;
  // Inline function 'kotlin.collections.forEach' call
  var _iterator__ex2g4s = this.y6_1.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (!(element === this.a8_1)) {
      element.kd();
    }
  }
};
protoOf(PanelScene).ld = function (spacingDegrees, immediate, respaceHandPlaced) {
  this.c7_1 = spacingDegrees;
  if (!immediate) {
    this.d7_1 = true;
    this.e7_1 = true;
    this.m7_1 = false;
    this.n7_1 = false;
  }
  if (immediate) {
    this.j7_1 = 0.0;
    this.k7_1 = 0.0;
  }
  var spacing = toRadians(spacingDegrees);
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.fa_1.ra_1 && (respaceHandPlaced || !element.ha_1)) {
      destination.e(element);
    }
  }
  var movable = destination;
  var centre = (movable.l() - 1 | 0) / 2.0;
  // Inline function 'kotlin.collections.forEachIndexed' call
  var index = 0;
  var _iterator__ex2g4s_0 = movable.g();
  while (_iterator__ex2g4s_0.h()) {
    var item = _iterator__ex2g4s_0.i();
    var _unary__edvuaz = index;
    index = _unary__edvuaz + 1 | 0;
    var slot = (checkIndexOverflow(_unary__edvuaz) - centre) * spacing;
    if (immediate) {
      item.nd(slot);
    } else {
      item.md(slot);
    }
  }
};
protoOf(PanelScene).od = function (spacingDegrees, immediate, respaceHandPlaced, $super) {
  spacingDegrees = spacingDegrees === VOID ? this.c7_1 : spacingDegrees;
  immediate = immediate === VOID ? true : immediate;
  respaceHandPlaced = respaceHandPlaced === VOID ? false : respaceHandPlaced;
  var tmp;
  if ($super === VOID) {
    this.ld(spacingDegrees, immediate, respaceHandPlaced);
    tmp = Unit_instance;
  } else {
    tmp = $super.ld.call(this, spacingDegrees, immediate, respaceHandPlaced);
  }
  return tmp;
};
protoOf(PanelScene).pd = function (panel, follow) {
  panel.fa_1 = follow;
  this.od(VOID, false);
};
protoOf(PanelScene).qd = function (panel, headYawRadians) {
  if (!panel.fa_1.ra_1 || panel.ha_1 || !this.y6_1.q(panel))
    return false;
  var heading = Companion_getInstance_1().la(headYawRadians - this.j7_1);
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.fa_1.ra_1 && !element.ha_1) {
      destination.e(element);
    }
  }
  var tmp$ret$3;
  $l$block_0: {
    // Inline function 'kotlin.collections.minByOrNull' call
    var iterator = destination.g();
    if (!iterator.h()) {
      tmp$ret$3 = null;
      break $l$block_0;
    }
    var minElem = iterator.i();
    if (!iterator.h()) {
      tmp$ret$3 = minElem;
      break $l$block_0;
    }
    var it = minElem;
    // Inline function 'kotlin.math.abs' call
    var x = Companion_getInstance_1().la(it.ga_1 - heading);
    var minValue = Math.abs(x);
    do {
      var e = iterator.i();
      // Inline function 'kotlin.math.abs' call
      var x_0 = Companion_getInstance_1().la(e.ga_1 - heading);
      var v = Math.abs(x_0);
      if (compareTo(minValue, v) > 0) {
        minElem = e;
        minValue = v;
      }
    }
     while (iterator.h());
    tmp$ret$3 = minElem;
  }
  var tmp0_elvis_lhs = tmp$ret$3;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var nearest = tmp;
  if (!(nearest === panel)) {
    replaceEach(this.y6_1, PanelScene$bringIntoView$lambda(panel, nearest));
    this.od(VOID, false);
  }
  return true;
};
protoOf(PanelScene).rd = function () {
  this.d7_1 = true;
  this.e7_1 = true;
  this.m7_1 = false;
  this.n7_1 = false;
};
protoOf(PanelScene).sd = function (panel) {
  this.dd(panel);
  this.od(VOID, false);
  panel.nd(panel.ga_1);
  panel.ea_1 = panel.ea_1.cb(Companion_getInstance_1().la(this.j7_1 + panel.ga_1));
  return panel;
};
protoOf(PanelScene).td = function (panel, pointerDirection) {
  var tmp0_elvis_lhs = aimAngles(this, pointerDirection);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var aim = tmp;
  this.a8_1 = panel;
  this.e9_1 = null;
  this.d8_1 = panel.ea_1.ma_1;
  this.e8_1 = panel.ea_1.na_1;
  this.b8_1 = Companion_getInstance_1().la(panel.ea_1.ma_1 - aim.wb_1);
  this.c8_1 = panel.ea_1.na_1 - aim.xb_1;
  return true;
};
protoOf(PanelScene).ud = function () {
  var tmp0_elvis_lhs = this.a8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var panel = tmp;
  this.a8_1 = null;
  panel.ea_1 = panel.ea_1.cb(this.d8_1, this.e8_1);
};
protoOf(PanelScene).vd = function (pointerDirection) {
  var tmp0_elvis_lhs = this.a8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var panel = tmp;
  var tmp1_elvis_lhs = aimAngles(this, pointerDirection);
  var tmp_0;
  if (tmp1_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp_0 = tmp1_elvis_lhs;
  }
  var aim = tmp_0;
  panel.ea_1 = panel.ea_1.cb(Companion_getInstance_1().la(aim.wb_1 + this.b8_1), coerceIn(aim.xb_1 + this.c8_1, -Companion_getInstance().aa_1, Companion_getInstance().aa_1));
};
protoOf(PanelScene).wd = function (headYawRadians) {
  var tmp0_elvis_lhs = this.a8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return null;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var panel = tmp;
  this.a8_1 = null;
  var tmp_0 = this.z6_1.j2();
  removeAll(tmp_0, PanelScene$endGrab$lambda(panel));
  if (panel.fa_1.ra_1) {
    panel.xd(Companion_getInstance_1().la(panel.ea_1.ma_1 - this.j7_1), true);
  }
  return panel;
};
protoOf(PanelScene).yd = function (headYawRadians, dt) {
  var tmp0_safe_receiver = this.y7_1;
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    if (dt > 0.0) {
      var tmp = this;
      // Inline function 'kotlin.math.abs' call
      var x = toDegrees(Companion_getInstance_1().la(headYawRadians - tmp0_safe_receiver));
      tmp.x7_1 = Math.abs(x) / dt;
    }
  }
  this.y7_1 = headYawRadians;
  this.z7_1 = this.x7_1 < 30.0 ? this.z7_1 + dt : 0.0;
  advanceOverlays(this, headYawRadians, dt);
  if (this.a9_1) {
    placePopup(this);
  }
  advanceResizeAnimations(this, dt);
  if (!(this.a8_1 == null))
    return false;
  if (!this.d7_1) {
    if (hasUnresolvedOverlap(this)) {
      this.d7_1 = true;
      this.e7_1 = true;
      this.f7_1 = true;
      this.g7_1.z2();
      this.m7_1 = false;
      this.i7_1 = 0.0;
      return advanceReturn(this, headYawRadians, dt);
    }
    if (!hasLeftTheBand(this, headYawRadians))
      return false;
    var catchUp = this.g8_1 > widestPanelRadians(this);
    if (!catchUp && (this.g8_1 < Companion_getInstance().t9_1 || this.z7_1 < 0.3)) {
      return false;
    }
    this.d7_1 = true;
    this.f7_1 = false;
    this.m7_1 = true;
    this.n7_1 = false;
    this.l7_1 = this.j7_1;
    this.k7_1 = headYawRadians;
    this.e7_1 = catchUp;
    var centred = catchUp ? this.w7_1 : nearestFollowerTo(this, headYawRadians);
    var tmp_0 = this;
    var tmp_1;
    if (centred == null) {
      tmp_1 = null;
    } else {
      // Inline function 'kotlin.takeIf' call
      var tmp_2;
      if (centred.fa_1.ra_1 && !centred.ha_1) {
        tmp_2 = centred;
      } else {
        tmp_2 = null;
      }
      tmp_1 = tmp_2;
    }
    var tmp2_safe_receiver = tmp_1;
    var tmp_3;
    if (tmp2_safe_receiver == null) {
      tmp_3 = null;
    } else {
      // Inline function 'kotlin.let' call
      tmp_3 = -tmp2_safe_receiver.ga_1;
    }
    var tmp3_elvis_lhs = tmp_3;
    tmp_0.i7_1 = tmp3_elvis_lhs == null ? 0.0 : tmp3_elvis_lhs;
  } else if (this.m7_1) {
    retargetOnHead(this, headYawRadians);
  }
  return advanceReturn(this, headYawRadians, dt);
};
protoOf(PanelScene).zd = function (headYawRadians) {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.a5('head ' + degrees(this, headYawRadians) + '\xB0 anchor ' + degrees(this, this.j7_1) + '\xB0 ');
  this_0.a5('target ' + degrees(this, this.k7_1) + '\xB0 offset ' + degrees(this, this.i7_1) + '\xB0 ');
  this_0.a5('returning=' + this.d7_1 + ' commanded=' + this.e7_1 + ' ');
  var tmp = this.f7_1;
  var tmp0_safe_receiver = this.a8_1;
  this_0.a5('inPlace=' + tmp + ' grabbed=' + (tmp0_safe_receiver == null ? null : tmp0_safe_receiver.ba_1) + ' ');
  this_0.a5('policy=' + this.b7_1.toString() + ' overlap=' + hasUnresolvedOverlap(this) + ' ');
  this_0.a5('band=' + hasLeftTheBand(this, headYawRadians));
  // Inline function 'kotlin.collections.sortedBy' call
  var this_1 = this.y6_1;
  // Inline function 'kotlin.comparisons.compareBy' call
  var tmp_0 = PanelScene$describe$lambda;
  var tmp$ret$4 = new sam$kotlin_Comparator$0_0(tmp_0);
  var _iterator__ex2g4s = sortedWith(this_1, tmp$ret$4).g();
  while (_iterator__ex2g4s.h()) {
    var panel = _iterator__ex2g4s.i();
    var tmp_1;
    if (panel.fa_1.ra_1) {
      tmp_1 = resolveTarget(this, panel, targetOf(this, panel, headYawRadians), headYawRadians, 0.0);
    } else {
      tmp_1 = null;
    }
    var resolved = tmp_1;
    this_0.a5('\n    ' + padEnd(panel.ba_1, 7) + ' at ' + degrees(this, panel.ea_1.ma_1, '+') + '\xB0 ');
    this_0.a5('slot ' + degrees(this, panel.ga_1, '+') + '\xB0');
    if (resolved == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      this_0.a5(' -> ' + degrees(this, resolved.za_1, '+') + '\xB0');
    }
    if ((resolved == null ? null : resolved.ab_1) === true) {
      this_0.a5(' CROSSING');
    }
    this_0.a5(!panel.fa_1.ra_1 ? ' pinned' : panel.ha_1 ? ' byHand' : '');
  }
  return this_0.toString();
};
protoOf(PanelScene).ae = function (panel, oldWidthPx, oldHeightPx, fixed, keepCentreX) {
  if (!this.y6_1.q(panel))
    return Unit_instance;
  var pose = panel.ea_1;
  var metresPerPx = pose.pa_1 / oldWidthPx;
  var width = panel.fb() * metresPerPx;
  var height = panel.gb() * metresPerPx;
  var distance = pose.oa_1;
  var yawShift = keepCentreX ? 0.0 : (-fixed.de_1 | 0) * (resized$halfAngle(distance, pose.pa_1) - resized$halfAngle(distance, width));
  var pitchShift = fixed.ee_1 * (resized$halfAngle(distance, pose.qa_1) - resized$halfAngle(distance, height));
  var tmp0 = this.i8_1;
  // Inline function 'kotlin.collections.set' call
  var value = new ResizeAnimation(pose.pa_1, pose.qa_1, pose.na_1, pose.ma_1, width, height, pose.na_1 + pitchShift, Companion_getInstance_1().la(pose.ma_1 + yawShift), !panel.fa_1.ra_1);
  tmp0.k2(panel, value);
  if (panel.fa_1.ra_1) {
    panel.xd(Companion_getInstance_1().la(panel.ga_1 + yawShift), panel.ha_1);
    this.rd();
  }
};
protoOf(PanelScene).fe = function (panel, oldWidthPx, oldHeightPx, fixed, keepCentreX, $super) {
  keepCentreX = keepCentreX === VOID ? false : keepCentreX;
  var tmp;
  if ($super === VOID) {
    this.ae(panel, oldWidthPx, oldHeightPx, fixed, keepCentreX);
    tmp = Unit_instance;
  } else {
    tmp = $super.ae.call(this, panel, oldWidthPx, oldHeightPx, fixed, keepCentreX);
  }
  return tmp;
};
protoOf(PanelScene).yb = function () {
  return !(this.k8_1 == null) && (this.m8_1 || this.y6_1.k());
};
protoOf(PanelScene).ge = function (dock, launcher) {
  this.j8_1 = dock;
  this.k8_1 = launcher;
};
protoOf(PanelScene).rb = function () {
  this.a9_1 = false;
  this.b9_1 = null;
};
protoOf(PanelScene).he = function (panel) {
  if (!this.y6_1.q(panel))
    return Unit_instance;
  this.v7_1 = panel;
  this.w7_1 = panel;
};
protoOf(PanelScene).ie = function (panel, headYawRadians) {
  if (!this.y6_1.q(panel) || !panel.fa_1.ra_1)
    return Unit_instance;
  this.d7_1 = true;
  this.e7_1 = true;
  this.f7_1 = false;
  this.m7_1 = false;
  this.n7_1 = false;
  this.k7_1 = headYawRadians;
  this.i7_1 = -panel.ga_1;
  this.e9_1 = panel;
  this.f9_1 = headYawRadians;
  this.g9_1 = 0;
};
protoOf(PanelScene).je = function () {
  var focus = this.v7_1;
  var held = this.a8_1;
  var tmp = PanelScene$drawOrder$lambda;
  var tmp_0 = PanelScene$drawOrder$lambda_0(held);
  return sortedWith(this.y6_1, compareBy([tmp, tmp_0, PanelScene$drawOrder$lambda_1(focus)]));
};
protoOf(PanelScene).ke = function (worldDirection) {
  var length = worldDirection.a();
  if (length < 1.0E-6)
    return null;
  var direction = worldDirection.xc(1.0 / length);
  this.x8_1 = direction;
  var tmp0_safe_receiver = this.z8_1;
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.takeIf' call
    var tmp_0;
    if (this.a9_1) {
      tmp_0 = tmp0_safe_receiver;
    } else {
      tmp_0 = null;
    }
    tmp = tmp_0;
  }
  var tmp_1 = tmp;
  var tmp1_safe_receiver = this.k8_1;
  var tmp_2;
  if (tmp1_safe_receiver == null) {
    tmp_2 = null;
  } else {
    // Inline function 'kotlin.takeIf' call
    var tmp_3;
    if (this.yb()) {
      tmp_3 = tmp1_safe_receiver;
    } else {
      tmp_3 = null;
    }
    tmp_2 = tmp_3;
  }
  var tmp_4 = tmp_2;
  var tmp2_safe_receiver = this.j8_1;
  var tmp_5;
  if (tmp2_safe_receiver == null) {
    tmp_5 = null;
  } else {
    // Inline function 'kotlin.takeIf' call
    var tmp_6;
    if (this.n8_1) {
      tmp_6 = tmp2_safe_receiver;
    } else {
      tmp_6 = null;
    }
    tmp_5 = tmp_6;
  }
  var tmp0 = listOfNotNull([tmp_1, tmp_4, tmp_5]);
  var tmp$ret$6;
  $l$block: {
    // Inline function 'kotlin.collections.firstNotNullOfOrNull' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      var result = intersect(this, element, direction, false);
      if (!(result == null)) {
        tmp$ret$6 = result;
        break $l$block;
      }
    }
    tmp$ret$6 = null;
  }
  var tmp3_safe_receiver = tmp$ret$6;
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp3_safe_receiver;
  }
  var tmp0_0 = asReversed(this.je());
  var tmp$ret$10;
  $l$block_0: {
    // Inline function 'kotlin.collections.firstNotNullOfOrNull' call
    var _iterator__ex2g4s_0 = tmp0_0.g();
    while (_iterator__ex2g4s_0.h()) {
      var element_0 = _iterator__ex2g4s_0.i();
      var result_0 = intersect(this, element_0, direction);
      if (!(result_0 == null)) {
        tmp$ret$10 = result_0;
        break $l$block_0;
      }
    }
    tmp$ret$10 = null;
  }
  var top = tmp$ret$10;
  if (!_get_focusHeld__vtgs78(this)) {
    if (top == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      this.v7_1 = top.u6_1;
      if (this.x7_1 < 30.0)
        this.w7_1 = top.u6_1;
    }
  }
  return top;
};
function CollisionPolicy_OVERLAP_getInstance() {
  CollisionPolicy_initEntries();
  return CollisionPolicy_OVERLAP_instance;
}
function CollisionPolicy_AVOID_PINNED_getInstance() {
  CollisionPolicy_initEntries();
  return CollisionPolicy_AVOID_PINNED_instance;
}
function CollisionPolicy_AVOID_ALL_getInstance() {
  CollisionPolicy_initEntries();
  return CollisionPolicy_AVOID_ALL_instance;
}
function DockVisibility_ALWAYS_getInstance() {
  DockVisibility_initEntries();
  return DockVisibility_ALWAYS_instance;
}
function DockVisibility_AUTO_HIDE_getInstance() {
  DockVisibility_initEntries();
  return DockVisibility_AUTO_HIDE_instance;
}
var Corner_TOP_LEFT_instance;
var Corner_TOP_RIGHT_instance;
var Corner_BOTTOM_LEFT_instance;
var Corner_BOTTOM_RIGHT_instance;
function values() {
  return [Corner_TOP_LEFT_getInstance(), Corner_TOP_RIGHT_getInstance(), Corner_BOTTOM_LEFT_getInstance(), Corner_BOTTOM_RIGHT_getInstance()];
}
function valueOf(value) {
  switch (value) {
    case 'TOP_LEFT':
      return Corner_TOP_LEFT_getInstance();
    case 'TOP_RIGHT':
      return Corner_TOP_RIGHT_getInstance();
    case 'BOTTOM_LEFT':
      return Corner_BOTTOM_LEFT_getInstance();
    case 'BOTTOM_RIGHT':
      return Corner_BOTTOM_RIGHT_getInstance();
    default:
      Corner_initEntries();
      THROW_IAE('No enum constant dev.halone.compositor.Corner.' + value);
      break;
  }
}
function get_entries() {
  if ($ENTRIES == null)
    $ENTRIES = enumEntries(values());
  return $ENTRIES;
}
var Corner_entriesInitialized;
function Corner_initEntries() {
  if (Corner_entriesInitialized)
    return Unit_instance;
  Corner_entriesInitialized = true;
  Corner_TOP_LEFT_instance = new Corner('TOP_LEFT', 0, -1, 1);
  Corner_TOP_RIGHT_instance = new Corner('TOP_RIGHT', 1, 1, 1);
  Corner_BOTTOM_LEFT_instance = new Corner('BOTTOM_LEFT', 2, -1, -1);
  Corner_BOTTOM_RIGHT_instance = new Corner('BOTTOM_RIGHT', 3, 1, -1);
}
var $ENTRIES;
function Corner(name, ordinal, sx, sy) {
  Enum.call(this, name, ordinal);
  this.de_1 = sx;
  this.ee_1 = sy;
}
protoOf(Corner).le = function () {
  var tmp0 = get_entries();
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.first' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (element.de_1 === (-this.de_1 | 0) && element.ee_1 === (-this.ee_1 | 0)) {
        tmp$ret$0 = element;
        break $l$block;
      }
    }
    throw NoSuchElementException_init_$Create$_0('Collection contains no element matching the predicate.');
  }
  return tmp$ret$0;
};
protoOf(Corner).me = function (widthPx, heightPx) {
  return new PanelHit(this.de_1 < 0 ? 0.0 : widthPx, this.ee_1 > 0 ? 0.0 : heightPx);
};
function ResizeHandles() {
  this.pc_1 = 14.0;
  this.qc_1 = 5.0;
  this.rc_1 = 28.0;
  this.sc_1 = 64.0;
  this.tc_1 = 40.0;
  this.uc_1 = 150.0;
}
protoOf(ResizeHandles).vc = function (widthPx, heightPx, x, y) {
  var w = widthPx;
  var h = heightPx;
  if ((0.0 <= x ? x <= w : false) && (0.0 <= y ? y <= h : false))
    return null;
  var tmp0 = get_entries();
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      var c = element.me(w, h);
      var outwardX = (x - c.s6_1) * element.de_1;
      var outwardY = (c.t6_1 - y) * element.ee_1;
      if ((-40.0 <= outwardX ? outwardX <= 64.0 : false) && (-40.0 <= outwardY ? outwardY <= 64.0 : false)) {
        tmp$ret$0 = element;
        break $l$block;
      }
    }
    tmp$ret$0 = null;
  }
  return tmp$ret$0;
};
var ResizeHandles_instance;
function ResizeHandles_getInstance() {
  return ResizeHandles_instance;
}
function Corner_TOP_LEFT_getInstance() {
  Corner_initEntries();
  return Corner_TOP_LEFT_instance;
}
function Corner_TOP_RIGHT_getInstance() {
  Corner_initEntries();
  return Corner_TOP_RIGHT_instance;
}
function Corner_BOTTOM_LEFT_getInstance() {
  Corner_initEntries();
  return Corner_BOTTOM_LEFT_instance;
}
function Corner_BOTTOM_RIGHT_getInstance() {
  Corner_initEntries();
  return Corner_BOTTOM_RIGHT_instance;
}
function PanelPose(yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres) {
  yawRadians = yawRadians === VOID ? 0.0 : yawRadians;
  pitchRadians = pitchRadians === VOID ? 0.0 : pitchRadians;
  distanceMetres = distanceMetres === VOID ? 4.0 : distanceMetres;
  widthMetres = widthMetres === VOID ? 1.28 : widthMetres;
  heightMetres = heightMetres === VOID ? 0.8 : heightMetres;
  this.ma_1 = yawRadians;
  this.na_1 = pitchRadians;
  this.oa_1 = distanceMetres;
  this.pa_1 = widthMetres;
  this.qa_1 = heightMetres;
}
protoOf(PanelPose).ne = function (yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres) {
  return new PanelPose(yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres);
};
protoOf(PanelPose).cb = function (yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres, $super) {
  yawRadians = yawRadians === VOID ? this.ma_1 : yawRadians;
  pitchRadians = pitchRadians === VOID ? this.na_1 : pitchRadians;
  distanceMetres = distanceMetres === VOID ? this.oa_1 : distanceMetres;
  widthMetres = widthMetres === VOID ? this.pa_1 : widthMetres;
  heightMetres = heightMetres === VOID ? this.qa_1 : heightMetres;
  return $super === VOID ? this.ne(yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres) : $super.ne.call(this, yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres);
};
protoOf(PanelPose).toString = function () {
  return 'PanelPose(yawRadians=' + this.ma_1 + ', pitchRadians=' + this.na_1 + ', distanceMetres=' + this.oa_1 + ', widthMetres=' + this.pa_1 + ', heightMetres=' + this.qa_1 + ')';
};
protoOf(PanelPose).hashCode = function () {
  var result = getNumberHashCode(this.ma_1);
  result = imul(result, 31) + getNumberHashCode(this.na_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.oa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.pa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.qa_1) | 0;
  return result;
};
protoOf(PanelPose).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PanelPose))
    return false;
  if (!equals(this.ma_1, other.ma_1))
    return false;
  if (!equals(this.na_1, other.na_1))
    return false;
  if (!equals(this.oa_1, other.oa_1))
    return false;
  if (!equals(this.pa_1, other.pa_1))
    return false;
  if (!equals(this.qa_1, other.qa_1))
    return false;
  return true;
};
function Companion_0() {
  Companion_instance_0 = this;
  this.oe_1 = new SmoothFollow(false);
}
var Companion_instance_0;
function Companion_getInstance_0() {
  if (Companion_instance_0 == null)
    new Companion_0();
  return Companion_instance_0;
}
function SmoothFollow(enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond) {
  Companion_getInstance_0();
  enabled = enabled === VOID ? true : enabled;
  deadzoneDegrees = deadzoneDegrees === VOID ? 12.0 : deadzoneDegrees;
  timeConstantSeconds = timeConstantSeconds === VOID ? 2.5 : timeConstantSeconds;
  followDegreesPerSecond = followDegreesPerSecond === VOID ? 6.0 : followDegreesPerSecond;
  crossingTimeConstantSeconds = crossingTimeConstantSeconds === VOID ? 0.25 : crossingTimeConstantSeconds;
  crossingDegreesPerSecond = crossingDegreesPerSecond === VOID ? 150.0 : crossingDegreesPerSecond;
  layoutTimeConstantSeconds = layoutTimeConstantSeconds === VOID ? 0.5 : layoutTimeConstantSeconds;
  layoutDegreesPerSecond = layoutDegreesPerSecond === VOID ? 60.0 : layoutDegreesPerSecond;
  this.ra_1 = enabled;
  this.sa_1 = deadzoneDegrees;
  this.ta_1 = timeConstantSeconds;
  this.ua_1 = followDegreesPerSecond;
  this.va_1 = crossingTimeConstantSeconds;
  this.wa_1 = crossingDegreesPerSecond;
  this.xa_1 = layoutTimeConstantSeconds;
  this.ya_1 = layoutDegreesPerSecond;
}
protoOf(SmoothFollow).pe = function (enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond) {
  return new SmoothFollow(enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond);
};
protoOf(SmoothFollow).qe = function (enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond, $super) {
  enabled = enabled === VOID ? this.ra_1 : enabled;
  deadzoneDegrees = deadzoneDegrees === VOID ? this.sa_1 : deadzoneDegrees;
  timeConstantSeconds = timeConstantSeconds === VOID ? this.ta_1 : timeConstantSeconds;
  followDegreesPerSecond = followDegreesPerSecond === VOID ? this.ua_1 : followDegreesPerSecond;
  crossingTimeConstantSeconds = crossingTimeConstantSeconds === VOID ? this.va_1 : crossingTimeConstantSeconds;
  crossingDegreesPerSecond = crossingDegreesPerSecond === VOID ? this.wa_1 : crossingDegreesPerSecond;
  layoutTimeConstantSeconds = layoutTimeConstantSeconds === VOID ? this.xa_1 : layoutTimeConstantSeconds;
  layoutDegreesPerSecond = layoutDegreesPerSecond === VOID ? this.ya_1 : layoutDegreesPerSecond;
  return $super === VOID ? this.pe(enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond) : $super.pe.call(this, enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond);
};
protoOf(SmoothFollow).toString = function () {
  return 'SmoothFollow(enabled=' + this.ra_1 + ', deadzoneDegrees=' + this.sa_1 + ', timeConstantSeconds=' + this.ta_1 + ', followDegreesPerSecond=' + this.ua_1 + ', crossingTimeConstantSeconds=' + this.va_1 + ', crossingDegreesPerSecond=' + this.wa_1 + ', layoutTimeConstantSeconds=' + this.xa_1 + ', layoutDegreesPerSecond=' + this.ya_1 + ')';
};
protoOf(SmoothFollow).hashCode = function () {
  var result = getBooleanHashCode(this.ra_1);
  result = imul(result, 31) + getNumberHashCode(this.sa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ta_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ua_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.va_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.wa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.xa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ya_1) | 0;
  return result;
};
protoOf(SmoothFollow).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof SmoothFollow))
    return false;
  if (!(this.ra_1 === other.ra_1))
    return false;
  if (!equals(this.sa_1, other.sa_1))
    return false;
  if (!equals(this.ta_1, other.ta_1))
    return false;
  if (!equals(this.ua_1, other.ua_1))
    return false;
  if (!equals(this.va_1, other.va_1))
    return false;
  if (!equals(this.wa_1, other.wa_1))
    return false;
  if (!equals(this.xa_1, other.xa_1))
    return false;
  if (!equals(this.ya_1, other.ya_1))
    return false;
  return true;
};
function Companion_1() {
  Companion_instance_1 = this;
  this.ja_1 = toRadians(0.5);
  this.ka_1 = 0.5;
}
protoOf(Companion_1).la = function (angle) {
  var a = angle;
  while (a > 3.141592653589793)
    a = a - 2.0 * 3.141592653589793;
  while (a < -3.141592653589793)
    a = a + 2.0 * 3.141592653589793;
  return a;
};
var Companion_instance_1;
function Companion_getInstance_1() {
  if (Companion_instance_1 == null)
    new Companion_1();
  return Companion_instance_1;
}
function ScenePanel(id, pose, follow) {
  Companion_getInstance_1();
  follow = follow === VOID ? new SmoothFollow() : follow;
  this.ba_1 = id;
  this.ca_1 = null;
  this.da_1 = false;
  this.ea_1 = pose;
  this.fa_1 = follow;
  this.ga_1 = pose.ma_1;
  this.ha_1 = false;
  this.ia_1 = false;
}
protoOf(ScenePanel).kd = function () {
  this.ea_1 = this.ea_1.cb(this.ga_1);
};
protoOf(ScenePanel).nd = function (yawRadians) {
  this.ga_1 = yawRadians;
  this.ha_1 = false;
  this.ea_1 = this.ea_1.cb(yawRadians);
};
protoOf(ScenePanel).xd = function (yawRadians, byHand) {
  this.ga_1 = yawRadians;
  this.ha_1 = byHand;
};
protoOf(ScenePanel).md = function (yawRadians, byHand, $super) {
  byHand = byHand === VOID ? false : byHand;
  var tmp;
  if ($super === VOID) {
    this.xd(yawRadians, byHand);
    tmp = Unit_instance;
  } else {
    tmp = $super.xd.call(this, yawRadians, byHand);
  }
  return tmp;
};
protoOf(ScenePanel).eb = function () {
  // Inline function 'kotlin.math.atan' call
  var x = this.ea_1.pa_1 / 2.0 / this.ea_1.oa_1;
  return Math.atan(x);
};
protoOf(ScenePanel).db = function () {
  // Inline function 'kotlin.math.atan' call
  var x = this.ea_1.qa_1 / 2.0 / this.ea_1.oa_1;
  return Math.atan(x);
};
protoOf(ScenePanel).bb = function (target, dt, timeConstantSeconds, minimumDegreesPerSecond, alreadyTriggered) {
  if (!this.fa_1.ra_1 || dt <= 0.0 || dt > 0.5)
    return null;
  var error = Companion_getInstance_1().la(target - this.ea_1.ma_1);
  var deadzone = toRadians(this.fa_1.sa_1);
  if (!alreadyTriggered && !this.ia_1) {
    // Inline function 'kotlin.math.abs' call
    if (Math.abs(error) <= deadzone)
      return null;
    this.ia_1 = true;
  }
  // Inline function 'kotlin.math.abs' call
  if (Math.abs(error) <= Companion_getInstance_1().ja_1) {
    this.ia_1 = false;
    return null;
  }
  var alpha = coerceAtMost(dt / timeConstantSeconds, 1.0);
  var step = error * alpha;
  // Inline function 'kotlin.comparisons.maxOf' call
  var b = this.fa_1.ua_1;
  var floorDegrees = Math.max(minimumDegreesPerSecond, b);
  var floor = toRadians(floorDegrees) * dt;
  // Inline function 'kotlin.math.abs' call
  var x = step;
  if (Math.abs(x) < floor) {
    // Inline function 'kotlin.math.sign' call
    var tmp = sign(error);
    // Inline function 'kotlin.math.abs' call
    // Inline function 'kotlin.math.min' call
    var b_0 = Math.abs(error);
    step = tmp * Math.min(floor, b_0);
  }
  return Companion_getInstance_1().la(this.ea_1.ma_1 + step);
};
var HitPart_CONTENT_instance;
var HitPart_TITLE_BAR_instance;
var HitPart_BACK_instance;
var HitPart_PIN_instance;
var HitPart_SIZE_instance;
var HitPart_CLOSE_instance;
var HitPart_RESIZE_TOP_LEFT_instance;
var HitPart_RESIZE_TOP_RIGHT_instance;
var HitPart_RESIZE_BOTTOM_LEFT_instance;
var HitPart_RESIZE_BOTTOM_RIGHT_instance;
function Companion_2() {
}
protoOf(Companion_2).wc = function (corner) {
  var tmp0 = get_entries_0();
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.first' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (equals(element.te_1, corner)) {
        tmp$ret$0 = element;
        break $l$block;
      }
    }
    throw NoSuchElementException_init_$Create$_0('Collection contains no element matching the predicate.');
  }
  return tmp$ret$0;
};
var Companion_instance_2;
function Companion_getInstance_2() {
  return Companion_instance_2;
}
function values_0() {
  return [HitPart_CONTENT_getInstance(), HitPart_TITLE_BAR_getInstance(), HitPart_BACK_getInstance(), HitPart_PIN_getInstance(), HitPart_SIZE_getInstance(), HitPart_CLOSE_getInstance(), HitPart_RESIZE_TOP_LEFT_getInstance(), HitPart_RESIZE_TOP_RIGHT_getInstance(), HitPart_RESIZE_BOTTOM_LEFT_getInstance(), HitPart_RESIZE_BOTTOM_RIGHT_getInstance()];
}
function get_entries_0() {
  if ($ENTRIES_0 == null)
    $ENTRIES_0 = enumEntries(values_0());
  return $ENTRIES_0;
}
var HitPart_entriesInitialized;
function HitPart_initEntries() {
  if (HitPart_entriesInitialized)
    return Unit_instance;
  HitPart_entriesInitialized = true;
  HitPart_CONTENT_instance = new HitPart('CONTENT', 0);
  HitPart_TITLE_BAR_instance = new HitPart('TITLE_BAR', 1);
  HitPart_BACK_instance = new HitPart('BACK', 2);
  HitPart_PIN_instance = new HitPart('PIN', 3);
  HitPart_SIZE_instance = new HitPart('SIZE', 4);
  HitPart_CLOSE_instance = new HitPart('CLOSE', 5);
  HitPart_RESIZE_TOP_LEFT_instance = new HitPart('RESIZE_TOP_LEFT', 6, Corner_TOP_LEFT_getInstance());
  HitPart_RESIZE_TOP_RIGHT_instance = new HitPart('RESIZE_TOP_RIGHT', 7, Corner_TOP_RIGHT_getInstance());
  HitPart_RESIZE_BOTTOM_LEFT_instance = new HitPart('RESIZE_BOTTOM_LEFT', 8, Corner_BOTTOM_LEFT_getInstance());
  HitPart_RESIZE_BOTTOM_RIGHT_instance = new HitPart('RESIZE_BOTTOM_RIGHT', 9, Corner_BOTTOM_RIGHT_getInstance());
}
var $ENTRIES_0;
function HitPart(name, ordinal, corner) {
  corner = corner === VOID ? null : corner;
  Enum.call(this, name, ordinal);
  this.te_1 = corner;
}
function AngularRect(yaw, halfWidth, bottom, top) {
  this.sb_1 = yaw;
  this.tb_1 = halfWidth;
  this.ub_1 = bottom;
  this.vb_1 = top;
}
protoOf(AngularRect).toString = function () {
  return 'AngularRect(yaw=' + this.sb_1 + ', halfWidth=' + this.tb_1 + ', bottom=' + this.ub_1 + ', top=' + this.vb_1 + ')';
};
protoOf(AngularRect).hashCode = function () {
  var result = getNumberHashCode(this.sb_1);
  result = imul(result, 31) + getNumberHashCode(this.tb_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ub_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.vb_1) | 0;
  return result;
};
protoOf(AngularRect).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof AngularRect))
    return false;
  if (!equals(this.sb_1, other.sb_1))
    return false;
  if (!equals(this.tb_1, other.tb_1))
    return false;
  if (!equals(this.ub_1, other.ub_1))
    return false;
  if (!equals(this.vb_1, other.vb_1))
    return false;
  return true;
};
function PanelRect(left, top, width, height) {
  this.ue_1 = left;
  this.ve_1 = top;
  this.we_1 = width;
  this.xe_1 = height;
}
protoOf(PanelRect).ye = function () {
  return this.ue_1 + this.we_1;
};
protoOf(PanelRect).ze = function () {
  return this.ve_1 + this.xe_1;
};
protoOf(PanelRect).af = function (x, y) {
  var tmp;
  var containsLower = this.ue_1;
  if (x <= this.ye() ? containsLower <= x : false) {
    var containsLower_0 = this.ve_1;
    tmp = y <= this.ze() ? containsLower_0 <= y : false;
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(PanelRect).toString = function () {
  return 'PanelRect(left=' + this.ue_1 + ', top=' + this.ve_1 + ', width=' + this.we_1 + ', height=' + this.xe_1 + ')';
};
protoOf(PanelRect).hashCode = function () {
  var result = getNumberHashCode(this.ue_1);
  result = imul(result, 31) + getNumberHashCode(this.ve_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.we_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.xe_1) | 0;
  return result;
};
protoOf(PanelRect).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PanelRect))
    return false;
  if (!equals(this.ue_1, other.ue_1))
    return false;
  if (!equals(this.ve_1, other.ve_1))
    return false;
  if (!equals(this.we_1, other.we_1))
    return false;
  if (!equals(this.xe_1, other.xe_1))
    return false;
  return true;
};
function button($this, bar, left) {
  return new PanelRect(left, bar.ve_1 + (bar.xe_1 - 40.0) / 2.0, 40.0, 40.0);
}
function TitleBar() {
  TitleBar_instance = this;
  this.dc_1 = 14.0;
  this.ec_1 = 56.0;
  this.fc_1 = 40.0;
  this.gc_1 = 6.0;
  this.hc_1 = 20.0;
  this.ic_1 = 10.0;
  this.jc_1 = 38.0;
  this.kc_1 = 0.45;
  this.lc_1 = 360.0;
  this.mc_1 = 640.0;
  this.nc_1 = listOf([to(1280, 720), to(720, 1280), to(1024, 768)]);
}
protoOf(TitleBar).bf = function (panelWidthPx, panelHeightPx) {
  var tmp = panelWidthPx * 0.45;
  // Inline function 'kotlin.math.min' call
  var tmp$ret$0 = Math.min(640.0, panelWidthPx);
  var width = coerceIn(tmp, 360.0, tmp$ret$0);
  return new PanelRect((panelWidthPx - width) / 2.0, panelHeightPx + 14.0, width, 56.0);
};
protoOf(TitleBar).cf = function (panelWidthPx, panelHeightPx) {
  var bar = this.bf(panelWidthPx, panelHeightPx);
  return button(this, bar, bar.ye() - 10.0 - 40.0);
};
protoOf(TitleBar).df = function (panelWidthPx, panelHeightPx) {
  var close = this.cf(panelWidthPx, panelHeightPx);
  return button(this, this.bf(panelWidthPx, panelHeightPx), close.ue_1 - 6.0 - 40.0);
};
protoOf(TitleBar).ef = function (panelWidthPx, panelHeightPx) {
  var pin = this.df(panelWidthPx, panelHeightPx);
  return button(this, this.bf(panelWidthPx, panelHeightPx), pin.ue_1 - 6.0 - 40.0);
};
protoOf(TitleBar).ff = function (panelWidthPx, panelHeightPx) {
  var bar = this.bf(panelWidthPx, panelHeightPx);
  return button(this, bar, bar.ue_1 + 38.0);
};
protoOf(TitleBar).oc = function (panelWidthPx, panelHeightPx, x, y, back, shape) {
  return !this.bf(panelWidthPx, panelHeightPx).af(x, y) ? null : back && this.ff(panelWidthPx, panelHeightPx).af(x, y) ? HitPart_BACK_getInstance() : this.cf(panelWidthPx, panelHeightPx).af(x, y) ? HitPart_CLOSE_getInstance() : this.df(panelWidthPx, panelHeightPx).af(x, y) ? HitPart_PIN_getInstance() : shape && this.ef(panelWidthPx, panelHeightPx).af(x, y) ? HitPart_SIZE_getInstance() : HitPart_TITLE_BAR_getInstance();
};
var TitleBar_instance;
function TitleBar_getInstance() {
  if (TitleBar_instance == null)
    new TitleBar();
  return TitleBar_instance;
}
function HitPart_CONTENT_getInstance() {
  HitPart_initEntries();
  return HitPart_CONTENT_instance;
}
function HitPart_TITLE_BAR_getInstance() {
  HitPart_initEntries();
  return HitPart_TITLE_BAR_instance;
}
function HitPart_BACK_getInstance() {
  HitPart_initEntries();
  return HitPart_BACK_instance;
}
function HitPart_PIN_getInstance() {
  HitPart_initEntries();
  return HitPart_PIN_instance;
}
function HitPart_SIZE_getInstance() {
  HitPart_initEntries();
  return HitPart_SIZE_instance;
}
function HitPart_CLOSE_getInstance() {
  HitPart_initEntries();
  return HitPart_CLOSE_instance;
}
function HitPart_RESIZE_TOP_LEFT_getInstance() {
  HitPart_initEntries();
  return HitPart_RESIZE_TOP_LEFT_instance;
}
function HitPart_RESIZE_TOP_RIGHT_getInstance() {
  HitPart_initEntries();
  return HitPart_RESIZE_TOP_RIGHT_instance;
}
function HitPart_RESIZE_BOTTOM_LEFT_getInstance() {
  HitPart_initEntries();
  return HitPart_RESIZE_BOTTOM_LEFT_instance;
}
function HitPart_RESIZE_BOTTOM_RIGHT_getInstance() {
  HitPart_initEntries();
  return HitPart_RESIZE_BOTTOM_RIGHT_instance;
}
function toRadians(degrees) {
  return degrees * 0.017453292519943295;
}
function toDegrees(radians) {
  return radians * 57.29577951308232;
}
function formatFixed(value, decimals, positivePrefix) {
  positivePrefix = positivePrefix === VOID ? '' : positivePrefix;
  var scale = new Long(1, 0);
  // Inline function 'kotlin.repeat' call
  var inductionVariable = 0;
  if (inductionVariable < decimals)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.Long.times' call
      var this_0 = scale;
      scale = multiply(this_0, fromInt(10));
    }
     while (inductionVariable < decimals);
  // Inline function 'kotlin.math.abs' call
  var tmp$ret$3 = Math.abs(value);
  var scaled = roundToLong(tmp$ret$3 * toNumber(scale));
  var whole = divide(scaled, scale);
  var fraction = padStart(modulo(scaled, scale).toString(), decimals, _Char___init__impl__6a9atx(48));
  var sign = value < 0 && !equalsLong(scaled, new Long(0, 0)) ? '-' : positivePrefix;
  return decimals === 0 ? sign + whole.toString() : sign + whole.toString() + '.' + fraction;
}
function Companion_3() {
  Companion_instance_3 = this;
  this.gf_1 = new Vec3(0.0, 0.0, 0.0);
}
var Companion_instance_3;
function Companion_getInstance_3() {
  if (Companion_instance_3 == null)
    new Companion_3();
  return Companion_instance_3;
}
function Vec3$toString$lambda(it) {
  return formatFixed(it, 4, ' ');
}
function Vec3(x, y, z) {
  Companion_getInstance_3();
  this.ac_1 = x;
  this.bc_1 = y;
  this.cc_1 = z;
}
protoOf(Vec3).xc = function (s) {
  return new Vec3(this.ac_1 * s, this.bc_1 * s, this.cc_1 * s);
};
protoOf(Vec3).a = function () {
  // Inline function 'kotlin.math.sqrt' call
  var x = this.ac_1 * this.ac_1 + this.bc_1 * this.bc_1 + this.cc_1 * this.cc_1;
  return Math.sqrt(x);
};
protoOf(Vec3).toString = function () {
  var tmp = listOf([this.ac_1, this.bc_1, this.cc_1]);
  return joinToString(tmp, ' ', VOID, VOID, VOID, VOID, Vec3$toString$lambda);
};
protoOf(Vec3).hashCode = function () {
  var result = getNumberHashCode(this.ac_1);
  result = imul(result, 31) + getNumberHashCode(this.bc_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.cc_1) | 0;
  return result;
};
protoOf(Vec3).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof Vec3))
    return false;
  if (!equals(this.ac_1, other.ac_1))
    return false;
  if (!equals(this.bc_1, other.bc_1))
    return false;
  if (!equals(this.cc_1, other.cc_1))
    return false;
  return true;
};
function concurrentPanelList() {
  return ArrayList_init_$Create$();
}
function replaceEach(_this__u8e3s4, transform) {
  var inductionVariable = 0;
  var last = _this__u8e3s4.l() - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      _this__u8e3s4.j1(i, transform(_this__u8e3s4.j(i)));
    }
     while (inductionVariable <= last);
}
function OverlayState(id, yawDegrees, pitchDegrees, opacity, shown) {
  this.id = id;
  this.yawDegrees = yawDegrees;
  this.pitchDegrees = pitchDegrees;
  this.opacity = opacity;
  this.shown = shown;
}
protoOf(OverlayState).hf = function () {
  return this.id;
};
protoOf(OverlayState).if = function () {
  return this.yawDegrees;
};
protoOf(OverlayState).jf = function () {
  return this.pitchDegrees;
};
protoOf(OverlayState).kf = function () {
  return this.opacity;
};
protoOf(OverlayState).lf = function () {
  return this.shown;
};
function PanelState(id, yawDegrees, pitchDegrees, slotDegrees, pinned, placedByHand) {
  this.id = id;
  this.yawDegrees = yawDegrees;
  this.pitchDegrees = pitchDegrees;
  this.slotDegrees = slotDegrees;
  this.pinned = pinned;
  this.placedByHand = placedByHand;
}
protoOf(PanelState).hf = function () {
  return this.id;
};
protoOf(PanelState).if = function () {
  return this.yawDegrees;
};
protoOf(PanelState).jf = function () {
  return this.pitchDegrees;
};
protoOf(PanelState).mf = function () {
  return this.slotDegrees;
};
protoOf(PanelState).nf = function () {
  return this.pinned;
};
protoOf(PanelState).of = function () {
  return this.placedByHand;
};
function panelOf($this, id, widthPx, heightPx) {
  var pose = new PanelPose(VOID, VOID, 4.0, metres($this, widthPx), metres($this, heightPx));
  return new SimulatorPanel(id, widthPx, heightPx, pose);
}
function overlayState($this, panel, opacity, shown) {
  return new OverlayState(panel.ba_1, Companion_instance_4.qf(panel.ea_1.ma_1), Companion_instance_4.qf(panel.ea_1.na_1), opacity, shown);
}
function panel($this, id) {
  var tmp0_elvis_lhs = $this.sf_1.jd(id);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    throw IllegalArgumentException_init_$Create$('no such panel: ' + id);
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function metres($this, px) {
  return 4.0 * px / $this.rf_1;
}
function Companion_4() {
  this.pf_1 = 4.0;
}
protoOf(Companion_4).tf = function (degrees) {
  return toRadians(degrees);
};
protoOf(Companion_4).qf = function (radians) {
  return toDegrees(radians);
};
protoOf(Companion_4).uf = function (yawDegrees, pitchDegrees) {
  var yaw = this.tf(yawDegrees);
  var pitch = this.tf(pitchDegrees);
  // Inline function 'kotlin.math.cos' call
  var tmp = -Math.cos(pitch);
  // Inline function 'kotlin.math.sin' call
  var tmp_0 = tmp * Math.sin(yaw);
  // Inline function 'kotlin.math.sin' call
  var tmp_1 = Math.sin(pitch);
  // Inline function 'kotlin.math.cos' call
  var tmp_2 = -Math.cos(pitch);
  // Inline function 'kotlin.math.cos' call
  var tmp$ret$4 = Math.cos(yaw);
  return new Vec3(tmp_0, tmp_1, tmp_2 * tmp$ret$4);
};
var Companion_instance_4;
function Companion_getInstance_4() {
  return Companion_instance_4;
}
function SimulatorScene(panelDistancePx) {
  this.rf_1 = panelDistancePx;
  this.sf_1 = new PanelScene();
}
protoOf(SimulatorScene).vf = function () {
  return Companion_instance_4.qf(this.sf_1.bd());
};
protoOf(SimulatorScene).wf = function () {
  return this.sf_1.cd();
};
protoOf(SimulatorScene).xf = function () {
  var tmp0_safe_receiver = this.sf_1.v7_1;
  return tmp0_safe_receiver == null ? null : tmp0_safe_receiver.ba_1;
};
protoOf(SimulatorScene).yf = function () {
  var tmp0_safe_receiver = this.sf_1.a8_1;
  return tmp0_safe_receiver == null ? null : tmp0_safe_receiver.ba_1;
};
protoOf(SimulatorScene).setCollisionPolicy = function (policy) {
  var tmp = this.sf_1;
  switch (policy) {
    case 'overlap':
      tmp.b7_1 = CollisionPolicy_OVERLAP_getInstance();
      break;
    case 'avoid-pinned':
      tmp.b7_1 = CollisionPolicy_AVOID_PINNED_getInstance();
      break;
    default:
      tmp.b7_1 = CollisionPolicy_AVOID_ALL_getInstance();
      break;
  }
};
protoOf(SimulatorScene).setFollowMarginDegrees = function (degrees) {
  this.sf_1.s7_1 = degrees;
};
protoOf(SimulatorScene).setCatchUpSeconds = function (seconds) {
  this.sf_1.p7_1 = seconds;
};
protoOf(SimulatorScene).setFollowsHead = function (follows) {
  this.sf_1.r7_1 = follows;
};
protoOf(SimulatorScene).setHeadPitchDegrees = function (degrees) {
  this.sf_1.y8_1 = Companion_instance_4.tf(degrees);
};
protoOf(SimulatorScene).setDockAutoHide = function (autoHide) {
  this.sf_1.l8_1 = autoHide ? DockVisibility_AUTO_HIDE_getInstance() : DockVisibility_ALWAYS_getInstance();
};
protoOf(SimulatorScene).setWindowsAutoHide = function (autoHide, delaySeconds) {
  this.sf_1.q8_1 = autoHide;
  this.sf_1.s8_1 = delaySeconds;
};
protoOf(SimulatorScene).zf = function () {
  return this.sf_1.u8_1;
};
protoOf(SimulatorScene).ag = function (value) {
  this.sf_1.m8_1 = value;
};
protoOf(SimulatorScene).bg = function () {
  return this.sf_1.m8_1;
};
protoOf(SimulatorScene).yb = function () {
  return this.sf_1.yb();
};
protoOf(SimulatorScene).openOverlays = function (dockId, dockWidthPx, dockHeightPx, launcherId, launcherWidthPx, launcherHeightPx) {
  this.sf_1.ge(panelOf(this, dockId, dockWidthPx, dockHeightPx), panelOf(this, launcherId, launcherWidthPx, launcherHeightPx));
};
protoOf(SimulatorScene).overlays = function () {
  var tmp0_safe_receiver = this.sf_1.j8_1;
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = overlayState(this, tmp0_safe_receiver, this.sf_1.o8_1, this.sf_1.n8_1);
  }
  var tmp_0 = tmp;
  var tmp1_safe_receiver = this.sf_1.k8_1;
  var tmp_1;
  if (tmp1_safe_receiver == null) {
    tmp_1 = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp_1 = overlayState(this, tmp1_safe_receiver, this.sf_1.p8_1, this.sf_1.yb());
  }
  // Inline function 'kotlin.collections.toTypedArray' call
  var this_0 = listOfNotNull([tmp_0, tmp_1]);
  return copyToArray(this_0);
};
protoOf(SimulatorScene).focusAndCentre = function (id, headYawDegrees) {
  var panel_0 = panel(this, id);
  this.sf_1.he(panel_0);
  this.sf_1.ie(panel_0, Companion_instance_4.tf(headYawDegrees));
};
protoOf(SimulatorScene).setSlotSpacingDegrees = function (degrees) {
  if (!(degrees === this.sf_1.c7_1)) {
    this.sf_1.od(degrees, false);
  }
};
protoOf(SimulatorScene).open = function (id, widthPx, heightPx) {
  this.sf_1.sd(panelOf(this, id, widthPx, heightPx));
};
protoOf(SimulatorScene).resize = function (id, widthPx, heightPx, draggedCorner) {
  var panel_0 = panel(this, id);
  var oldWidth = panel_0.kg_1;
  var oldHeight = panel_0.lg_1;
  panel_0.kg_1 = widthPx;
  panel_0.lg_1 = heightPx;
  this.sf_1.fe(panel_0, oldWidth, oldHeight, valueOf(draggedCorner).le());
};
protoOf(SimulatorScene).close = function (id) {
  this.sf_1.ed(panel(this, id));
  this.sf_1.od(VOID, false);
};
protoOf(SimulatorScene).setPinned = function (id, pinned) {
  var panel_0 = panel(this, id);
  if (panel_0.fa_1.ra_1 === pinned) {
    this.sf_1.pd(panel_0, panel_0.fa_1.qe(!pinned));
  }
};
protoOf(SimulatorScene).bringIntoView = function (id, headYawDegrees) {
  return this.sf_1.qd(panel(this, id), Companion_instance_4.tf(headYawDegrees));
};
protoOf(SimulatorScene).recentre = function () {
  return this.sf_1.kd();
};
protoOf(SimulatorScene).advance = function (headYawDegrees, dt) {
  return this.sf_1.yd(Companion_instance_4.tf(headYawDegrees), dt);
};
protoOf(SimulatorScene).hitTest = function (yawDegrees, pitchDegrees) {
  var tmp0_safe_receiver = this.sf_1.ke(Companion_instance_4.uf(yawDegrees, pitchDegrees));
  var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.u6_1;
  return tmp1_safe_receiver == null ? null : tmp1_safe_receiver.ba_1;
};
protoOf(SimulatorScene).beginGrab = function (id, aimYawDegrees, aimPitchDegrees) {
  return this.sf_1.td(panel(this, id), Companion_instance_4.uf(aimYawDegrees, aimPitchDegrees));
};
protoOf(SimulatorScene).updateGrab = function (aimYawDegrees, aimPitchDegrees) {
  return this.sf_1.vd(Companion_instance_4.uf(aimYawDegrees, aimPitchDegrees));
};
protoOf(SimulatorScene).endGrab = function (headYawDegrees) {
  this.sf_1.wd(Companion_instance_4.tf(headYawDegrees));
};
protoOf(SimulatorScene).cancelGrab = function () {
  return this.sf_1.ud();
};
protoOf(SimulatorScene).titleBar = function (widthPx, heightPx) {
  // Inline function 'kotlin.let' call
  var it = TitleBar_getInstance().bf(widthPx, heightPx);
  // Inline function 'kotlin.arrayOf' call
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return [it.ue_1, it.ve_1, it.we_1, it.xe_1];
};
protoOf(SimulatorScene).panels = function () {
  // Inline function 'kotlin.collections.map' call
  var this_0 = this.sf_1.ad();
  // Inline function 'kotlin.collections.mapTo' call
  var destination = ArrayList_init_$Create$_0(collectionSizeOrDefault(this_0, 10));
  var _iterator__ex2g4s = this_0.g();
  while (_iterator__ex2g4s.h()) {
    var item = _iterator__ex2g4s.i();
    var tmp$ret$2 = new PanelState(item.ba_1, Companion_instance_4.qf(item.ea_1.ma_1), Companion_instance_4.qf(item.ea_1.na_1), Companion_instance_4.qf(item.ga_1), !item.fa_1.ra_1, item.ha_1);
    destination.e(tmp$ret$2);
  }
  // Inline function 'kotlin.collections.toTypedArray' call
  return copyToArray(destination);
};
protoOf(SimulatorScene).describe = function (headYawDegrees) {
  return this.sf_1.zd(Companion_instance_4.tf(headYawDegrees));
};
function SimulatorPanel(id, widthPx, heightPx, pose) {
  ScenePanel.call(this, id, pose);
  this.kg_1 = widthPx;
  this.lg_1 = heightPx;
}
protoOf(SimulatorPanel).fb = function () {
  return this.kg_1;
};
protoOf(SimulatorPanel).gb = function () {
  return this.lg_1;
};
//region block: post-declaration
defineProp(protoOf(SimulatorScene), 'anchorYawDegrees', protoOf(SimulatorScene).vf);
defineProp(protoOf(SimulatorScene), 'returning', protoOf(SimulatorScene).wf);
defineProp(protoOf(SimulatorScene), 'focusedId', protoOf(SimulatorScene).xf);
defineProp(protoOf(SimulatorScene), 'grabbedId', protoOf(SimulatorScene).yf);
defineProp(protoOf(SimulatorScene), 'windowsOpacity', protoOf(SimulatorScene).zf);
defineProp(protoOf(SimulatorScene), 'launcherRequested', protoOf(SimulatorScene).bg, protoOf(SimulatorScene).ag);
defineProp(protoOf(SimulatorScene), 'launcherShown', protoOf(SimulatorScene).yb);
//endregion
//region block: init
ResizeHandles_instance = new ResizeHandles();
Companion_instance_2 = new Companion_2();
Companion_instance_4 = new Companion_4();
//endregion
//region block: exports
export {
  OverlayState as OverlayState,
  PanelState as PanelState,
  SimulatorScene as SimulatorScene,
};
//endregion


