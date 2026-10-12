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
    tmp_1 = excessFromOrigin < -Companion_getInstance().v9_1;
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
  return $this.f7_1 ? panel.da_1.la_1 : Companion_getInstance_1().ka($this.j7_1 + panel.fa_1);
}
function advanceReturn($this, headYawRadians, dt) {
  var anchorMoved = false;
  if (!$this.f7_1 && dt > 0.0 && dt <= 0.5) {
    var target = $this.n7_1 ? $this.l7_1 : Companion_getInstance_1().ka($this.k7_1 + $this.i7_1);
    var error = Companion_getInstance_1().ka(target - $this.j7_1);
    // Inline function 'kotlin.math.abs' call
    if (Math.abs(error) > Companion_getInstance().p9_1) {
      var seconds = $this.e7_1 ? $this.p7_1 : $this.o7_1.sa_1;
      var perSecond = $this.e7_1 ? $this.q7_1 : $this.o7_1.ta_1;
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
      $this.j7_1 = Companion_getInstance_1().ka($this.j7_1 + step);
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
    if ($this.f7_1 && !$this.g7_1.q(panel.aa_1)) {
      var tmp0_0 = $this.a7_1;
      var tmp2 = panel.aa_1;
      // Inline function 'kotlin.collections.set' call
      var value = panel.da_1.la_1;
      tmp0_0.k2(tmp2, value);
      continue $l$loop_1;
    }
    var wanted = targetOf($this, panel, headYawRadians);
    var resolved = resolveTarget($this, panel, wanted, headYawRadians, dt);
    var tmp0_1 = $this.a7_1;
    var tmp2_0 = panel.aa_1;
    // Inline function 'kotlin.collections.set' call
    var value_0 = resolved.ya_1;
    tmp0_1.k2(tmp2_0, value_0);
    if (resolved.za_1)
      crossing = true;
    var timeConstant = resolved.za_1 ? panel.ea_1.ua_1 : $this.e7_1 ? $this.p7_1 : $this.t7_1 ? 0.1 : panel.ea_1.sa_1;
    var floor_0 = resolved.za_1 ? panel.ea_1.va_1 : $this.e7_1 ? $this.q7_1 : 0.0;
    var tmp0_elvis_lhs = panel.ab(resolved.ya_1, dt, timeConstant, floor_0, true);
    var tmp_1;
    if (tmp0_elvis_lhs == null) {
      continue $l$loop_1;
    } else {
      tmp_1 = tmp0_elvis_lhs;
    }
    var next = tmp_1;
    if (next === panel.da_1.la_1)
      continue $l$loop_1;
    panel.da_1 = panel.da_1.bb(next);
    moved = true;
  }
  $this.f8_1 = crossing;
  $this.h7_1.z2();
  // Inline function 'kotlin.collections.forEach' call
  var _iterator__ex2g4s_0 = $this.y6_1.g();
  while (_iterator__ex2g4s_0.h()) {
    var element = _iterator__ex2g4s_0.i();
    var tmp0_2 = $this.h7_1;
    var tmp2_1 = element.aa_1;
    // Inline function 'kotlin.collections.set' call
    var value_1 = element.da_1.la_1;
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
    if (!panel.ea_1.qa_1 || panel === $this.a8_1)
      continue $l$loop;
    var _iterator__ex2g4s_0 = $this.y6_1.g();
    $l$loop_2: while (_iterator__ex2g4s_0.h()) {
      var other = _iterator__ex2g4s_0.i();
      if (other === panel || other === $this.a8_1)
        continue $l$loop_2;
      if (!yieldsTo($this, panel, other))
        continue $l$loop_2;
      // Inline function 'kotlin.math.abs' call
      var x = panel.da_1.ma_1 - other.da_1.ma_1;
      var pitchGap = Math.abs(x);
      if (pitchGap > panel.cb() + other.cb()) {
        continue $l$loop_2;
      }
      // Inline function 'kotlin.math.abs' call
      var x_0 = Companion_getInstance_1().ka(panel.da_1.la_1 - other.da_1.la_1);
      var separation = Math.abs(x_0);
      if (separation < panel.db() + other.db()) {
        // Inline function 'kotlin.collections.plusAssign' call
        var element = panel.aa_1;
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
  return !other.ea_1.qa_1 ? true : $this.b7_1.equals(CollisionPolicy_AVOID_PINNED_getInstance()) ? false : panel.ga_1 ? false : true;
}
function nearestFollowerTo($this, headYawRadians) {
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = $this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.ea_1.qa_1 && !element.ga_1) {
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
    var x = Companion_getInstance_1().ka($this.j7_1 + it.fa_1 - headYawRadians);
    var minValue = Math.abs(x);
    do {
      var e = iterator.i();
      // Inline function 'kotlin.math.abs' call
      var x_0 = Companion_getInstance_1().ka($this.j7_1 + e.fa_1 - headYawRadians);
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
    if (element.ea_1.qa_1) {
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
    var maxValue = 2.0 * iterator.i().db();
    while (iterator.h()) {
      var v = 2.0 * iterator.i().db();
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
    if (element.ea_1.qa_1) {
      destination.e(element);
    }
  }
  var followers = destination;
  if (followers.k())
    return null;
  var error = Companion_getInstance_1().ka(headYawRadians - anchor);
  var margin = toRadians($this.s7_1);
  // Inline function 'kotlin.collections.maxOf' call
  var iterator = followers.g();
  if (!iterator.h())
    throw NoSuchElementException_init_$Create$();
  var it = iterator.i();
  var maxValue = it.fa_1 + it.db();
  while (iterator.h()) {
    var it_0 = iterator.i();
    var v = it_0.fa_1 + it_0.db();
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
  var minValue = it_1.fa_1 - it_1.db();
  while (iterator_0.h()) {
    var it_2 = iterator_0.i();
    var v_0 = it_2.fa_1 - it_2.db();
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
  this.ya_1 = target;
  this.za_1 = crossing;
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
    var x = panel.da_1.ma_1 - other.da_1.ma_1;
    var pitchGap = Math.abs(x);
    if (pitchGap > panel.cb() + other.cb())
      continue $l$loop_2;
    if (!yieldsTo($this, panel, other))
      continue $l$loop_2;
    if (other.ea_1.qa_1) {
      var separation = panel.db() + other.db() + Companion_getInstance().q9_1;
      var needed = separation + Companion_getInstance().x9_1;
      var tmp;
      if ($this.f7_1) {
        tmp = other.da_1.la_1;
      } else {
        var tmp0_elvis_lhs = $this.a7_1.w(other.aa_1);
        tmp = tmp0_elvis_lhs == null ? targetOf($this, other, headYawRadians) : tmp0_elvis_lhs;
      }
      var otherTarget = tmp;
      var order = Companion_getInstance_1().ka(panel.fa_1 - other.fa_1);
      var goPositive = !(order === 0.0) ? order > 0.0 : compareTo(panel.aa_1, other.aa_1) > 0;
      var gap = Companion_getInstance_1().ka(target - otherTarget);
      if (goPositive && gap < needed) {
        target = Companion_getInstance_1().ka(otherTarget + needed);
      } else if (!goPositive && gap > -needed) {
        target = Companion_getInstance_1().ka(otherTarget - needed);
      }
      continue $l$loop_2;
    }
    var separation_0 = panel.db() + other.db() + Companion_getInstance().q9_1;
    var here = Companion_getInstance_1().ka(panel.da_1.la_1 - other.da_1.la_1);
    var there = Companion_getInstance_1().ka(target - other.da_1.la_1);
    var gaze = Companion_getInstance_1().ka(headYawRadians - other.da_1.la_1);
    var edge = other.db();
    var key = panel.aa_1 + '\x00' + other.aa_1;
    var tmp1_elvis_lhs = $this.z6_1.w(key);
    var committed = tmp1_elvis_lhs == null ? there >= 0.0 : tmp1_elvis_lhs;
    // Inline function 'kotlin.math.abs' call
    var clear = Math.abs(here) >= separation_0;
    var arrived = clear && here >= 0.0 === committed;
    var reference = other.ea_1.qa_1 ? there : gaze;
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
    var targetSide = Companion_getInstance_1().ka(target - other.da_1.la_1) >= 0.0;
    if (!clear || !(targetSide === here >= 0.0))
      crossing = true;
    var parking = separation_0 + (other.ea_1.qa_1 ? Companion_getInstance().x9_1 : Companion_getInstance().w9_1);
    if (goPositive_0 && there < parking) {
      target = Companion_getInstance_1().ka(other.da_1.la_1 + parking);
    } else if (!goPositive_0 && there > -parking) {
      target = Companion_getInstance_1().ka(other.da_1.la_1 - parking);
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
      var x_0 = panel.da_1.ma_1 - element.da_1.ma_1;
      var pitchGap_0 = Math.abs(x_0);
      if (pitchGap_0 > panel.cb() + element.cb()) {
        tmp$ret$7 = null;
        break $l$block_1;
      }
      var separation_1 = panel.db() + element.db() + Companion_getInstance().q9_1;
      var tmp_1;
      if (element.ea_1.qa_1) {
        var tmp_2;
        if ($this.f7_1) {
          tmp_2 = element.da_1.la_1;
        } else {
          var tmp0_elvis_lhs_0 = $this.a7_1.w(element.aa_1);
          tmp_2 = tmp0_elvis_lhs_0 == null ? targetOf($this, element, headYawRadians) : tmp0_elvis_lhs_0;
        }
        tmp_1 = tmp_2;
      } else {
        tmp_1 = element.da_1.la_1;
      }
      var centre = tmp_1;
      var half = separation_1 + (element.ea_1.qa_1 ? Companion_getInstance().x9_1 : Companion_getInstance().w9_1);
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
      var list = listOf([Companion_getInstance_1().ka(centre_0 + half_0), Companion_getInstance_1().ka(centre_0 - half_0)]);
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
      var x_1 = Companion_getInstance_1().ka(it - wanted);
      var minValue = Math.abs(x_1);
      do {
        var e = iterator.i();
        // Inline function 'kotlin.math.abs' call
        var x_2 = Companion_getInstance_1().ka(e - wanted);
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
      var x_3 = Companion_getInstance_1().ka(candidate - target);
      if (Math.abs(x_3) > Companion_getInstance().o9_1) {
        crossing = true;
      }
      target = candidate;
    }
  }
  var displaced = !(target === wanted) || ($this.f7_1 && $this.g7_1.q(panel.aa_1));
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
        if (element_2 === panel || element_2 === $this.a8_1 || !element_2.ea_1.qa_1) {
          tmp$ret$27 = null;
          break $l$block_6;
        }
        // Inline function 'kotlin.math.abs' call
        var x_4 = panel.da_1.ma_1 - element_2.da_1.ma_1;
        var pitchGap_1 = Math.abs(x_4);
        if (pitchGap_1 > panel.cb() + element_2.cb()) {
          tmp$ret$27 = null;
          break $l$block_6;
        }
        var centre_1 = element_2.da_1.la_1;
        var tmp0_elvis_lhs_1 = $this.h7_1.w(element_2.aa_1);
        var previous = tmp0_elvis_lhs_1 == null ? centre_1 : tmp0_elvis_lhs_1;
        // Inline function 'kotlin.math.abs' call
        var x_5 = Companion_getInstance_1().ka(centre_1 - previous);
        if (Math.abs(x_5) > Companion_getInstance().y9_1) {
          tmp$ret$27 = null;
          break $l$block_6;
        }
        tmp$ret$27 = to(centre_1, panel.db() + element_2.db() + Companion_getInstance().q9_1 + Companion_getInstance().x9_1);
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
        var list_0 = listOf([Companion_getInstance_1().ka(centre_2 + half_1), Companion_getInstance_1().ka(centre_2 - half_1)]);
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
        var x_6 = Companion_getInstance_1().ka(it_0 - wanted);
        var minValue_0 = Math.abs(x_6);
        do {
          var e_0 = iterator_0.i();
          // Inline function 'kotlin.math.abs' call
          var x_7 = Companion_getInstance_1().ka(e_0 - wanted);
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
    var pose = item.da_1;
    var metresPerPx = pose.oa_1 / item.eb();
    var distance = pose.na_1;
    var handles = 64.0;
    // Inline function 'kotlin.comparisons.maxOf' call
    var a = 14.0 + 56.0;
    var below = Math.max(a, handles);
    // Inline function 'kotlin.math.atan' call
    var x = (item.eb() / 2.0 + handles) * metresPerPx / distance;
    var tmp = Math.atan(x);
    // Inline function 'kotlin.math.atan' call
    var x_0 = (item.fb() / 2.0 + below) * metresPerPx / distance;
    var tmp$ret$5 = Math.atan(x_0);
    var tmp_0 = pose.ma_1 - tmp$ret$5;
    // Inline function 'kotlin.math.atan' call
    var x_1 = (item.fb() / 2.0 + handles) * metresPerPx / distance;
    var tmp$ret$6 = Math.atan(x_1);
    var tmp$ret$2 = new AngularRect(pose.la_1, tmp, tmp_0, pose.ma_1 + tmp$ret$6);
    destination.e(tmp$ret$2);
  }
  return destination;
}
function ResizeAnimation(fromWidth, fromHeight, fromPitch, fromYaw, toWidth, toHeight, toPitch, toYaw, movesYaw) {
  this.gb_1 = fromWidth;
  this.hb_1 = fromHeight;
  this.ib_1 = fromPitch;
  this.jb_1 = fromYaw;
  this.kb_1 = toWidth;
  this.lb_1 = toHeight;
  this.mb_1 = toPitch;
  this.nb_1 = toYaw;
  this.ob_1 = movesYaw;
  this.pb_1 = 0.0;
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
    animation.pb_1 = coerceAtMost(animation.pb_1 + dt / 0.22, 1.0);
    // Inline function 'kotlin.let' call
    var it = 1.0 - animation.pb_1;
    var t = 1.0 - it * it * it;
    var tmp = panel;
    var tmp0_$this = panel.da_1;
    var tmp1_widthMetres = advanceResizeAnimations$lerp(t, animation.gb_1, animation.kb_1);
    var tmp2_heightMetres = advanceResizeAnimations$lerp(t, animation.hb_1, animation.lb_1);
    var tmp3_pitchRadians = advanceResizeAnimations$lerp(t, animation.ib_1, animation.mb_1);
    var tmp_0;
    if (animation.ob_1) {
      tmp_0 = Companion_getInstance_1().ka(animation.jb_1 + Companion_getInstance_1().ka(animation.nb_1 - animation.jb_1) * t);
    } else {
      tmp_0 = panel.da_1.la_1;
    }
    var tmp4_yawRadians = tmp_0;
    tmp.da_1 = tmp0_$this.bb(tmp4_yawRadians, tmp3_pitchRadians, VOID, tmp1_widthMetres, tmp2_heightMetres);
    if (animation.pb_1 >= 1.0) {
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
  var tmp0_elvis_lhs = $this.y8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var popup = tmp;
  var tmp1_elvis_lhs = $this.a9_1;
  var tmp_0;
  if (tmp1_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp_0 = tmp1_elvis_lhs;
  }
  var parent = tmp_0;
  if (!$this.y6_1.q(parent) && !(parent === $this.j8_1) && !(parent === $this.k8_1)) {
    $this.qb();
    return Unit_instance;
  }
  var pose = parent.da_1;
  var metresPerPx = pose.oa_1 / parent.eb();
  var width = popup.eb();
  var height = popup.fb();
  var left = placePopup$fit($this.b9_1, width, parent.eb());
  var top = placePopup$fit($this.c9_1, height, parent.fb());
  var distance = pose.na_1;
  var right = (left + width / 2.0 - parent.eb() / 2.0) * metresPerPx;
  var down = (top + height / 2.0 - parent.fb() / 2.0) * metresPerPx;
  var tmp_1 = popup;
  var tmp_2 = Companion_getInstance_1();
  // Inline function 'kotlin.math.atan' call
  var x = right / distance;
  var tmp$ret$0 = Math.atan(x);
  var tmp_3 = tmp_2.ka(pose.la_1 - tmp$ret$0);
  // Inline function 'kotlin.math.atan' call
  var x_0 = down / distance;
  var tmp$ret$1 = Math.atan(x_0);
  tmp_1.da_1 = pose.bb(tmp_3, pose.ma_1 - tmp$ret$1, VOID, width * metresPerPx, height * metresPerPx);
}
function correctCentring($this) {
  var tmp0_elvis_lhs = $this.d9_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var panel = tmp;
  if (!$this.y6_1.q(panel) || $this.f9_1 >= 2) {
    $this.d9_1 = null;
    return false;
  }
  var error = Companion_getInstance_1().ka($this.e9_1 - panel.da_1.la_1);
  // Inline function 'kotlin.math.abs' call
  if (Math.abs(error) <= Companion_getInstance().g9_1) {
    $this.d9_1 = null;
    return false;
  }
  $this.f9_1 = $this.f9_1 + 1 | 0;
  $this.k7_1 = Companion_getInstance_1().ka($this.k7_1 + $this.i7_1 + error);
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
  var previous = $this.v8_1;
  var yaw = previous == null || dt <= 0.0 ? headYawRadians : Companion_getInstance_1().ka(previous + Companion_getInstance_1().ka(headYawRadians - previous) * coerceAtMost(dt / 0.15, 1.0));
  $this.v8_1 = yaw;
  var tmp0 = windowReach($this);
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.minOfOrNull' call
    var iterator = tmp0.g();
    if (!iterator.h()) {
      tmp$ret$0 = null;
      break $l$block;
    }
    var minValue = iterator.i().tb_1;
    while (iterator.h()) {
      var v = iterator.i().tb_1;
      // Inline function 'kotlin.comparisons.minOf' call
      var a = minValue;
      minValue = Math.min(a, v);
    }
    tmp$ret$0 = minValue;
  }
  var windowsBottom = tmp$ret$0;
  var dockHalf = dock.cb();
  var tmp1_safe_receiver = $this.k8_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.cb();
  var launcherHalf = tmp2_elvis_lhs == null ? 0.0 : tmp2_elvis_lhs;
  var resting = -(launcherHalf + Companion_getInstance().m9_1 + dockHalf);
  var tmp_0;
  if (windowsBottom == null) {
    tmp_0 = resting;
  } else {
    // Inline function 'kotlin.comparisons.minOf' call
    var b = windowsBottom - Companion_getInstance().l9_1 - dockHalf;
    tmp_0 = Math.min(resting, b);
  }
  var dockPitch = tmp_0;
  dock.da_1 = dock.da_1.bb(yaw, dockPitch);
  var tmp3_safe_receiver = $this.k8_1;
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    tmp3_safe_receiver.da_1 = tmp3_safe_receiver.da_1.bb(yaw, dockPitch + dockHalf + Companion_getInstance().m9_1 + launcherHalf);
  }
  var tmp_1 = $this;
  var tmp_2;
  if (windowsBottom == null || $this.xb() || $this.l8_1.equals(DockVisibility_ALWAYS_getInstance())) {
    tmp_2 = true;
  } else if (!($this.a8_1 == null)) {
    tmp_2 = $this.n8_1;
  } else {
    var aim = $this.w8_1;
    var tmp_3;
    if (aim == null) {
      tmp_3 = null;
    } else {
      // Inline function 'kotlin.let' call
      var tmp0_safe_receiver = aimAngles($this, aim);
      tmp_3 = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.wb_1;
    }
    var pitches = listOfNotNull([tmp_3, $this.x8_1]);
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
              if (!(element_0 > windowsBottom + Companion_getInstance().n9_1)) {
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
  $this.p8_1 = approachLinearly($this, $this.p8_1, $this.xb() ? 1.0 : 0.0, fade);
  advanceWindowsVisibility($this, dt, fade);
}
function advanceWindowsVisibility($this, dt, fade) {
  var aim = $this.w8_1;
  var held = !$this.q8_1 || !($this.a8_1 == null) || !($this.h8_1 == null) || $this.xb() || $this.z8_1;
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
  $this.u8_1 = held || pointed || aim == null ? 0.0 : $this.u8_1 + dt;
  $this.s8_1 = held || pointed || $this.u8_1 < $this.r8_1;
  $this.t8_1 = approachLinearly($this, $this.t8_1, $this.s8_1 ? 1.0 : 0.0, fade);
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
  var pose = panel.da_1;
  var local = Companion_getInstance().yb(direction, pose.la_1, pose.ma_1);
  if (local.bc_1 >= -1.0E-4)
    return null;
  var t = -pose.na_1 / local.bc_1;
  if (t <= 0.0)
    return null;
  var u = local.zb_1 * t / pose.oa_1 + 0.5;
  var v = 0.5 - local.ac_1 * t / pose.pa_1;
  var x = u * panel.eb();
  var y = v * panel.fb();
  var tmp;
  if ((0.0 <= u ? u <= 1.0 : false) && (0.0 <= v ? v <= 1.0 : false)) {
    tmp = HitPart_CONTENT_getInstance();
  } else if (chrome) {
    var tmp0_elvis_lhs = TitleBar_instance.mc(panel.eb(), panel.fb(), x, y, panel.ca_1);
    var tmp_0;
    if (tmp0_elvis_lhs == null) {
      var tmp1_safe_receiver = ResizeHandles_instance.tc(panel.eb(), panel.fb(), x, y);
      var tmp_1;
      if (tmp1_safe_receiver == null) {
        tmp_1 = null;
      } else {
        // Inline function 'kotlin.let' call
        tmp_1 = Companion_instance_2.uc(tmp1_safe_receiver);
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
  this.vb_1 = yaw;
  this.wb_1 = pitch;
}
function aimAngles($this, direction) {
  var length = direction.a();
  if (length < 1.0E-6)
    return null;
  var d = direction.vc(1.0 / length);
  var tmp0 = -d.zb_1;
  // Inline function 'kotlin.math.atan2' call
  var x = -d.bc_1;
  var tmp = Math.atan2(tmp0, x);
  // Inline function 'kotlin.math.asin' call
  var x_0 = coerceIn(d.ac_1, -1.0, 1.0);
  var tmp$ret$1 = Math.asin(x_0);
  return new Aim(tmp, tmp$ret$1);
}
function Companion() {
  Companion_instance = this;
  this.g9_1 = toRadians(1.0);
  this.h9_1 = 2;
  this.i9_1 = 0.22;
  this.j9_1 = 0.15;
  this.k9_1 = 0.15;
  this.l9_1 = toRadians(1.6);
  this.m9_1 = toRadians(0.8);
  this.n9_1 = toRadians(1.5);
  this.o9_1 = toRadians(0.01);
  this.p9_1 = toRadians(0.5);
  this.q9_1 = toRadians(2.0);
  this.r9_1 = 30.0;
  this.s9_1 = toRadians(6.0);
  this.t9_1 = 0.3;
  this.u9_1 = 0.1;
  this.v9_1 = toRadians(3.0);
  this.w9_1 = toRadians(6.0);
  this.x9_1 = toRadians(2.0);
  this.y9_1 = toRadians(0.05);
  this.z9_1 = toRadians(25.0);
}
protoOf(Companion).yb = function (direction, yawRadians, pitchRadians) {
  // Inline function 'kotlin.math.cos' call
  var cy = Math.cos(yawRadians);
  // Inline function 'kotlin.math.sin' call
  var sy = Math.sin(yawRadians);
  var x = cy * direction.zb_1 - sy * direction.bc_1;
  var zYaw = sy * direction.zb_1 + cy * direction.bc_1;
  if (pitchRadians === 0.0)
    return new Vec3(x, direction.ac_1, zYaw);
  // Inline function 'kotlin.math.cos' call
  var cp = Math.cos(pitchRadians);
  // Inline function 'kotlin.math.sin' call
  var sp = Math.sin(pitchRadians);
  return new Vec3(x, cp * direction.ac_1 + sp * zYaw, -sp * direction.ac_1 + cp * zYaw);
};
var Companion_instance;
function Companion_getInstance() {
  if (Companion_instance == null)
    new Companion();
  return Companion_instance;
}
function sam$kotlin_Comparator$0(function_0) {
  this.wc_1 = function_0;
}
protoOf(sam$kotlin_Comparator$0).l5 = function (a, b) {
  return this.wc_1(a, b);
};
protoOf(sam$kotlin_Comparator$0).compare = function (a, b) {
  return this.l5(a, b);
};
protoOf(sam$kotlin_Comparator$0).i1 = function () {
  return this.wc_1;
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
  this.xc_1 = function_0;
}
protoOf(sam$kotlin_Comparator$0_0).l5 = function (a, b) {
  return this.xc_1(a, b);
};
protoOf(sam$kotlin_Comparator$0_0).compare = function (a, b) {
  return this.l5(a, b);
};
protoOf(sam$kotlin_Comparator$0_0).i1 = function () {
  return this.xc_1;
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
    return startsWith(it, $panel.aa_1 + '\x00') || endsWith(it, '\x00' + $panel.aa_1);
  };
}
function PanelScene$bringIntoView$lambda($panel, $nearest) {
  return function (it) {
    return it === $panel ? $nearest : it === $nearest ? $panel : it;
  };
}
function PanelScene$endGrab$lambda($panel) {
  return function (it) {
    return startsWith(it, $panel.aa_1 + '\x00');
  };
}
function PanelScene$advanceReturn$lambda(a, b) {
  // Inline function 'kotlin.comparisons.compareValuesBy' call
  var tmp = a.fa_1;
  var tmp$ret$2 = b.fa_1;
  return compareValues(tmp, tmp$ret$2);
}
function PanelScene$describe$lambda(a, b) {
  // Inline function 'kotlin.comparisons.compareValuesBy' call
  var tmp = a.fa_1;
  var tmp$ret$2 = b.fa_1;
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
      var x = Companion_getInstance_1().ka(at - centre);
      if (Math.abs(x) < half - Companion_getInstance().o9_1) {
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
      var x = Companion_getInstance_1().ka(at - centre);
      if (Math.abs(x) < half - Companion_getInstance().o9_1) {
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
  return -it.da_1.na_1;
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
  this.r8_1 = 1.0;
  this.s8_1 = true;
  this.t8_1 = 1.0;
  this.u8_1 = 0.0;
  this.v8_1 = null;
  this.w8_1 = null;
  this.x8_1 = null;
  this.y8_1 = null;
  this.z8_1 = false;
  this.a9_1 = null;
  this.b9_1 = 0.0;
  this.c9_1 = 0.0;
  this.d9_1 = null;
  this.e9_1 = 0.0;
  this.f9_1 = 0;
}
protoOf(PanelScene).yc = function () {
  return this.y6_1;
};
protoOf(PanelScene).zc = function () {
  return this.j7_1;
};
protoOf(PanelScene).ad = function () {
  return this.d7_1;
};
protoOf(PanelScene).bd = function (panel) {
  this.y6_1.e(panel);
  if (this.v7_1 == null)
    this.v7_1 = panel;
  return panel;
};
protoOf(PanelScene).cd = function (panel) {
  this.y6_1.k1(panel);
  this.i8_1.b3(panel);
  var tmp0_safe_receiver = this.h8_1;
  if ((tmp0_safe_receiver == null ? null : tmp0_safe_receiver.dd_1) === panel)
    this.h8_1 = null;
  this.a7_1.b3(panel.aa_1);
  if (this.a8_1 === panel)
    this.a8_1 = null;
  if (this.w7_1 === panel)
    this.w7_1 = null;
  var tmp = this.z6_1.j2();
  removeAll(tmp, PanelScene$remove$lambda(panel));
  if (this.v7_1 === panel)
    this.v7_1 = firstOrNull(this.y6_1);
};
protoOf(PanelScene).hd = function (id) {
  var tmp0 = this.y6_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (element.aa_1 === id) {
        tmp$ret$0 = element;
        break $l$block;
      }
    }
    tmp$ret$0 = null;
  }
  return tmp$ret$0;
};
protoOf(PanelScene).id = function () {
  this.j7_1 = 0.0;
  this.k7_1 = 0.0;
  // Inline function 'kotlin.collections.forEach' call
  var _iterator__ex2g4s = this.y6_1.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (!(element === this.a8_1)) {
      element.id();
    }
  }
};
protoOf(PanelScene).jd = function (spacingDegrees, immediate, respaceHandPlaced) {
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
    if (element.ea_1.qa_1 && (respaceHandPlaced || !element.ga_1)) {
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
      item.ld(slot);
    } else {
      item.kd(slot);
    }
  }
};
protoOf(PanelScene).md = function (spacingDegrees, immediate, respaceHandPlaced, $super) {
  spacingDegrees = spacingDegrees === VOID ? this.c7_1 : spacingDegrees;
  immediate = immediate === VOID ? true : immediate;
  respaceHandPlaced = respaceHandPlaced === VOID ? false : respaceHandPlaced;
  var tmp;
  if ($super === VOID) {
    this.jd(spacingDegrees, immediate, respaceHandPlaced);
    tmp = Unit_instance;
  } else {
    tmp = $super.jd.call(this, spacingDegrees, immediate, respaceHandPlaced);
  }
  return tmp;
};
protoOf(PanelScene).nd = function (panel, follow) {
  panel.ea_1 = follow;
  this.md(VOID, false);
};
protoOf(PanelScene).od = function (panel, headYawRadians) {
  if (!panel.ea_1.qa_1 || panel.ga_1 || !this.y6_1.q(panel))
    return false;
  var heading = Companion_getInstance_1().ka(headYawRadians - this.j7_1);
  // Inline function 'kotlin.collections.filter' call
  var tmp0 = this.y6_1;
  // Inline function 'kotlin.collections.filterTo' call
  var destination = ArrayList_init_$Create$();
  var _iterator__ex2g4s = tmp0.g();
  while (_iterator__ex2g4s.h()) {
    var element = _iterator__ex2g4s.i();
    if (element.ea_1.qa_1 && !element.ga_1) {
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
    var x = Companion_getInstance_1().ka(it.fa_1 - heading);
    var minValue = Math.abs(x);
    do {
      var e = iterator.i();
      // Inline function 'kotlin.math.abs' call
      var x_0 = Companion_getInstance_1().ka(e.fa_1 - heading);
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
    this.md(VOID, false);
  }
  return true;
};
protoOf(PanelScene).pd = function () {
  this.d7_1 = true;
  this.e7_1 = true;
  this.m7_1 = false;
  this.n7_1 = false;
};
protoOf(PanelScene).qd = function (panel) {
  this.bd(panel);
  this.md(VOID, false);
  panel.ld(panel.fa_1);
  panel.da_1 = panel.da_1.bb(Companion_getInstance_1().ka(this.j7_1 + panel.fa_1));
  return panel;
};
protoOf(PanelScene).rd = function (panel, pointerDirection) {
  var tmp0_elvis_lhs = aimAngles(this, pointerDirection);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var aim = tmp;
  this.a8_1 = panel;
  this.d9_1 = null;
  this.d8_1 = panel.da_1.la_1;
  this.e8_1 = panel.da_1.ma_1;
  this.b8_1 = Companion_getInstance_1().ka(panel.da_1.la_1 - aim.vb_1);
  this.c8_1 = panel.da_1.ma_1 - aim.wb_1;
  return true;
};
protoOf(PanelScene).sd = function () {
  var tmp0_elvis_lhs = this.a8_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var panel = tmp;
  this.a8_1 = null;
  panel.da_1 = panel.da_1.bb(this.d8_1, this.e8_1);
};
protoOf(PanelScene).td = function (pointerDirection) {
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
  panel.da_1 = panel.da_1.bb(Companion_getInstance_1().ka(aim.vb_1 + this.b8_1), coerceIn(aim.wb_1 + this.c8_1, -Companion_getInstance().z9_1, Companion_getInstance().z9_1));
};
protoOf(PanelScene).ud = function (headYawRadians) {
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
  if (panel.ea_1.qa_1) {
    panel.vd(Companion_getInstance_1().ka(panel.da_1.la_1 - this.j7_1), true);
  }
  return panel;
};
protoOf(PanelScene).wd = function (headYawRadians, dt) {
  var tmp0_safe_receiver = this.y7_1;
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    if (dt > 0.0) {
      var tmp = this;
      // Inline function 'kotlin.math.abs' call
      var x = toDegrees(Companion_getInstance_1().ka(headYawRadians - tmp0_safe_receiver));
      tmp.x7_1 = Math.abs(x) / dt;
    }
  }
  this.y7_1 = headYawRadians;
  this.z7_1 = this.x7_1 < 30.0 ? this.z7_1 + dt : 0.0;
  advanceOverlays(this, headYawRadians, dt);
  if (this.z8_1) {
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
    if (!catchUp && (this.g8_1 < Companion_getInstance().s9_1 || this.z7_1 < 0.3)) {
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
      if (centred.ea_1.qa_1 && !centred.ga_1) {
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
      tmp_3 = -tmp2_safe_receiver.fa_1;
    }
    var tmp3_elvis_lhs = tmp_3;
    tmp_0.i7_1 = tmp3_elvis_lhs == null ? 0.0 : tmp3_elvis_lhs;
  } else if (this.m7_1) {
    retargetOnHead(this, headYawRadians);
  }
  return advanceReturn(this, headYawRadians, dt);
};
protoOf(PanelScene).xd = function (headYawRadians) {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.a5('head ' + degrees(this, headYawRadians) + '\xB0 anchor ' + degrees(this, this.j7_1) + '\xB0 ');
  this_0.a5('target ' + degrees(this, this.k7_1) + '\xB0 offset ' + degrees(this, this.i7_1) + '\xB0 ');
  this_0.a5('returning=' + this.d7_1 + ' commanded=' + this.e7_1 + ' ');
  var tmp = this.f7_1;
  var tmp0_safe_receiver = this.a8_1;
  this_0.a5('inPlace=' + tmp + ' grabbed=' + (tmp0_safe_receiver == null ? null : tmp0_safe_receiver.aa_1) + ' ');
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
    if (panel.ea_1.qa_1) {
      tmp_1 = resolveTarget(this, panel, targetOf(this, panel, headYawRadians), headYawRadians, 0.0);
    } else {
      tmp_1 = null;
    }
    var resolved = tmp_1;
    this_0.a5('\n    ' + padEnd(panel.aa_1, 7) + ' at ' + degrees(this, panel.da_1.la_1, '+') + '\xB0 ');
    this_0.a5('slot ' + degrees(this, panel.fa_1, '+') + '\xB0');
    if (resolved == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      this_0.a5(' -> ' + degrees(this, resolved.ya_1, '+') + '\xB0');
    }
    if ((resolved == null ? null : resolved.za_1) === true) {
      this_0.a5(' CROSSING');
    }
    this_0.a5(!panel.ea_1.qa_1 ? ' pinned' : panel.ga_1 ? ' byHand' : '');
  }
  return this_0.toString();
};
protoOf(PanelScene).yd = function (panel, oldWidthPx, oldHeightPx, fixed) {
  if (!this.y6_1.q(panel))
    return Unit_instance;
  var pose = panel.da_1;
  var metresPerPx = pose.oa_1 / oldWidthPx;
  var width = panel.eb() * metresPerPx;
  var height = panel.fb() * metresPerPx;
  var distance = pose.na_1;
  var yawShift = (-fixed.be_1 | 0) * (resized$halfAngle(distance, pose.oa_1) - resized$halfAngle(distance, width));
  var pitchShift = fixed.ce_1 * (resized$halfAngle(distance, pose.pa_1) - resized$halfAngle(distance, height));
  var tmp0 = this.i8_1;
  // Inline function 'kotlin.collections.set' call
  var value = new ResizeAnimation(pose.oa_1, pose.pa_1, pose.ma_1, pose.la_1, width, height, pose.ma_1 + pitchShift, Companion_getInstance_1().ka(pose.la_1 + yawShift), !panel.ea_1.qa_1);
  tmp0.k2(panel, value);
  if (panel.ea_1.qa_1) {
    panel.vd(Companion_getInstance_1().ka(panel.fa_1 + yawShift), panel.ga_1);
    this.pd();
  }
};
protoOf(PanelScene).xb = function () {
  return !(this.k8_1 == null) && (this.m8_1 || this.y6_1.k());
};
protoOf(PanelScene).de = function (dock, launcher) {
  this.j8_1 = dock;
  this.k8_1 = launcher;
};
protoOf(PanelScene).qb = function () {
  this.z8_1 = false;
  this.a9_1 = null;
};
protoOf(PanelScene).ee = function (panel) {
  if (!this.y6_1.q(panel))
    return Unit_instance;
  this.v7_1 = panel;
  this.w7_1 = panel;
};
protoOf(PanelScene).fe = function (panel, headYawRadians) {
  if (!this.y6_1.q(panel) || !panel.ea_1.qa_1)
    return Unit_instance;
  this.d7_1 = true;
  this.e7_1 = true;
  this.f7_1 = false;
  this.m7_1 = false;
  this.n7_1 = false;
  this.k7_1 = headYawRadians;
  this.i7_1 = -panel.fa_1;
  this.d9_1 = panel;
  this.e9_1 = headYawRadians;
  this.f9_1 = 0;
};
protoOf(PanelScene).ge = function () {
  var focus = this.v7_1;
  var held = this.a8_1;
  var tmp = PanelScene$drawOrder$lambda;
  var tmp_0 = PanelScene$drawOrder$lambda_0(held);
  return sortedWith(this.y6_1, compareBy([tmp, tmp_0, PanelScene$drawOrder$lambda_1(focus)]));
};
protoOf(PanelScene).he = function (worldDirection) {
  var length = worldDirection.a();
  if (length < 1.0E-6)
    return null;
  var direction = worldDirection.vc(1.0 / length);
  this.w8_1 = direction;
  var tmp0_safe_receiver = this.y8_1;
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.takeIf' call
    var tmp_0;
    if (this.z8_1) {
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
    if (this.xb()) {
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
  var tmp0_0 = asReversed(this.ge());
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
  this.be_1 = sx;
  this.ce_1 = sy;
}
protoOf(Corner).ie = function () {
  var tmp0 = get_entries();
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.first' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (element.be_1 === (-this.be_1 | 0) && element.ce_1 === (-this.ce_1 | 0)) {
        tmp$ret$0 = element;
        break $l$block;
      }
    }
    throw NoSuchElementException_init_$Create$_0('Collection contains no element matching the predicate.');
  }
  return tmp$ret$0;
};
protoOf(Corner).je = function (widthPx, heightPx) {
  return new PanelHit(this.be_1 < 0 ? 0.0 : widthPx, this.ce_1 > 0 ? 0.0 : heightPx);
};
function ResizeHandles() {
  this.nc_1 = 14.0;
  this.oc_1 = 5.0;
  this.pc_1 = 28.0;
  this.qc_1 = 64.0;
  this.rc_1 = 40.0;
  this.sc_1 = 150.0;
}
protoOf(ResizeHandles).tc = function (widthPx, heightPx, x, y) {
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
      var c = element.je(w, h);
      var outwardX = (x - c.s6_1) * element.be_1;
      var outwardY = (c.t6_1 - y) * element.ce_1;
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
  this.la_1 = yawRadians;
  this.ma_1 = pitchRadians;
  this.na_1 = distanceMetres;
  this.oa_1 = widthMetres;
  this.pa_1 = heightMetres;
}
protoOf(PanelPose).ke = function (yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres) {
  return new PanelPose(yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres);
};
protoOf(PanelPose).bb = function (yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres, $super) {
  yawRadians = yawRadians === VOID ? this.la_1 : yawRadians;
  pitchRadians = pitchRadians === VOID ? this.ma_1 : pitchRadians;
  distanceMetres = distanceMetres === VOID ? this.na_1 : distanceMetres;
  widthMetres = widthMetres === VOID ? this.oa_1 : widthMetres;
  heightMetres = heightMetres === VOID ? this.pa_1 : heightMetres;
  return $super === VOID ? this.ke(yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres) : $super.ke.call(this, yawRadians, pitchRadians, distanceMetres, widthMetres, heightMetres);
};
protoOf(PanelPose).toString = function () {
  return 'PanelPose(yawRadians=' + this.la_1 + ', pitchRadians=' + this.ma_1 + ', distanceMetres=' + this.na_1 + ', widthMetres=' + this.oa_1 + ', heightMetres=' + this.pa_1 + ')';
};
protoOf(PanelPose).hashCode = function () {
  var result = getNumberHashCode(this.la_1);
  result = imul(result, 31) + getNumberHashCode(this.ma_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.na_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.oa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.pa_1) | 0;
  return result;
};
protoOf(PanelPose).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PanelPose))
    return false;
  if (!equals(this.la_1, other.la_1))
    return false;
  if (!equals(this.ma_1, other.ma_1))
    return false;
  if (!equals(this.na_1, other.na_1))
    return false;
  if (!equals(this.oa_1, other.oa_1))
    return false;
  if (!equals(this.pa_1, other.pa_1))
    return false;
  return true;
};
function Companion_0() {
  Companion_instance_0 = this;
  this.le_1 = new SmoothFollow(false);
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
  this.qa_1 = enabled;
  this.ra_1 = deadzoneDegrees;
  this.sa_1 = timeConstantSeconds;
  this.ta_1 = followDegreesPerSecond;
  this.ua_1 = crossingTimeConstantSeconds;
  this.va_1 = crossingDegreesPerSecond;
  this.wa_1 = layoutTimeConstantSeconds;
  this.xa_1 = layoutDegreesPerSecond;
}
protoOf(SmoothFollow).me = function (enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond) {
  return new SmoothFollow(enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond);
};
protoOf(SmoothFollow).ne = function (enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond, $super) {
  enabled = enabled === VOID ? this.qa_1 : enabled;
  deadzoneDegrees = deadzoneDegrees === VOID ? this.ra_1 : deadzoneDegrees;
  timeConstantSeconds = timeConstantSeconds === VOID ? this.sa_1 : timeConstantSeconds;
  followDegreesPerSecond = followDegreesPerSecond === VOID ? this.ta_1 : followDegreesPerSecond;
  crossingTimeConstantSeconds = crossingTimeConstantSeconds === VOID ? this.ua_1 : crossingTimeConstantSeconds;
  crossingDegreesPerSecond = crossingDegreesPerSecond === VOID ? this.va_1 : crossingDegreesPerSecond;
  layoutTimeConstantSeconds = layoutTimeConstantSeconds === VOID ? this.wa_1 : layoutTimeConstantSeconds;
  layoutDegreesPerSecond = layoutDegreesPerSecond === VOID ? this.xa_1 : layoutDegreesPerSecond;
  return $super === VOID ? this.me(enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond) : $super.me.call(this, enabled, deadzoneDegrees, timeConstantSeconds, followDegreesPerSecond, crossingTimeConstantSeconds, crossingDegreesPerSecond, layoutTimeConstantSeconds, layoutDegreesPerSecond);
};
protoOf(SmoothFollow).toString = function () {
  return 'SmoothFollow(enabled=' + this.qa_1 + ', deadzoneDegrees=' + this.ra_1 + ', timeConstantSeconds=' + this.sa_1 + ', followDegreesPerSecond=' + this.ta_1 + ', crossingTimeConstantSeconds=' + this.ua_1 + ', crossingDegreesPerSecond=' + this.va_1 + ', layoutTimeConstantSeconds=' + this.wa_1 + ', layoutDegreesPerSecond=' + this.xa_1 + ')';
};
protoOf(SmoothFollow).hashCode = function () {
  var result = getBooleanHashCode(this.qa_1);
  result = imul(result, 31) + getNumberHashCode(this.ra_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.sa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ta_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ua_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.va_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.wa_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.xa_1) | 0;
  return result;
};
protoOf(SmoothFollow).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof SmoothFollow))
    return false;
  if (!(this.qa_1 === other.qa_1))
    return false;
  if (!equals(this.ra_1, other.ra_1))
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
  return true;
};
function Companion_1() {
  Companion_instance_1 = this;
  this.ia_1 = toRadians(0.5);
  this.ja_1 = 0.5;
}
protoOf(Companion_1).ka = function (angle) {
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
  this.aa_1 = id;
  this.ba_1 = null;
  this.ca_1 = false;
  this.da_1 = pose;
  this.ea_1 = follow;
  this.fa_1 = pose.la_1;
  this.ga_1 = false;
  this.ha_1 = false;
}
protoOf(ScenePanel).id = function () {
  this.da_1 = this.da_1.bb(this.fa_1);
};
protoOf(ScenePanel).ld = function (yawRadians) {
  this.fa_1 = yawRadians;
  this.ga_1 = false;
  this.da_1 = this.da_1.bb(yawRadians);
};
protoOf(ScenePanel).vd = function (yawRadians, byHand) {
  this.fa_1 = yawRadians;
  this.ga_1 = byHand;
};
protoOf(ScenePanel).kd = function (yawRadians, byHand, $super) {
  byHand = byHand === VOID ? false : byHand;
  var tmp;
  if ($super === VOID) {
    this.vd(yawRadians, byHand);
    tmp = Unit_instance;
  } else {
    tmp = $super.vd.call(this, yawRadians, byHand);
  }
  return tmp;
};
protoOf(ScenePanel).db = function () {
  // Inline function 'kotlin.math.atan' call
  var x = this.da_1.oa_1 / 2.0 / this.da_1.na_1;
  return Math.atan(x);
};
protoOf(ScenePanel).cb = function () {
  // Inline function 'kotlin.math.atan' call
  var x = this.da_1.pa_1 / 2.0 / this.da_1.na_1;
  return Math.atan(x);
};
protoOf(ScenePanel).ab = function (target, dt, timeConstantSeconds, minimumDegreesPerSecond, alreadyTriggered) {
  if (!this.ea_1.qa_1 || dt <= 0.0 || dt > 0.5)
    return null;
  var error = Companion_getInstance_1().ka(target - this.da_1.la_1);
  var deadzone = toRadians(this.ea_1.ra_1);
  if (!alreadyTriggered && !this.ha_1) {
    // Inline function 'kotlin.math.abs' call
    if (Math.abs(error) <= deadzone)
      return null;
    this.ha_1 = true;
  }
  // Inline function 'kotlin.math.abs' call
  if (Math.abs(error) <= Companion_getInstance_1().ia_1) {
    this.ha_1 = false;
    return null;
  }
  var alpha = coerceAtMost(dt / timeConstantSeconds, 1.0);
  var step = error * alpha;
  // Inline function 'kotlin.comparisons.maxOf' call
  var b = this.ea_1.ta_1;
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
  return Companion_getInstance_1().ka(this.da_1.la_1 + step);
};
var HitPart_CONTENT_instance;
var HitPart_TITLE_BAR_instance;
var HitPart_BACK_instance;
var HitPart_PIN_instance;
var HitPart_CLOSE_instance;
var HitPart_RESIZE_TOP_LEFT_instance;
var HitPart_RESIZE_TOP_RIGHT_instance;
var HitPart_RESIZE_BOTTOM_LEFT_instance;
var HitPart_RESIZE_BOTTOM_RIGHT_instance;
function Companion_2() {
}
protoOf(Companion_2).uc = function (corner) {
  var tmp0 = get_entries_0();
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlin.collections.first' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (equals(element.qe_1, corner)) {
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
  return [HitPart_CONTENT_getInstance(), HitPart_TITLE_BAR_getInstance(), HitPart_BACK_getInstance(), HitPart_PIN_getInstance(), HitPart_CLOSE_getInstance(), HitPart_RESIZE_TOP_LEFT_getInstance(), HitPart_RESIZE_TOP_RIGHT_getInstance(), HitPart_RESIZE_BOTTOM_LEFT_getInstance(), HitPart_RESIZE_BOTTOM_RIGHT_getInstance()];
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
  HitPart_CLOSE_instance = new HitPart('CLOSE', 4);
  HitPart_RESIZE_TOP_LEFT_instance = new HitPart('RESIZE_TOP_LEFT', 5, Corner_TOP_LEFT_getInstance());
  HitPart_RESIZE_TOP_RIGHT_instance = new HitPart('RESIZE_TOP_RIGHT', 6, Corner_TOP_RIGHT_getInstance());
  HitPart_RESIZE_BOTTOM_LEFT_instance = new HitPart('RESIZE_BOTTOM_LEFT', 7, Corner_BOTTOM_LEFT_getInstance());
  HitPart_RESIZE_BOTTOM_RIGHT_instance = new HitPart('RESIZE_BOTTOM_RIGHT', 8, Corner_BOTTOM_RIGHT_getInstance());
}
var $ENTRIES_0;
function HitPart(name, ordinal, corner) {
  corner = corner === VOID ? null : corner;
  Enum.call(this, name, ordinal);
  this.qe_1 = corner;
}
function AngularRect(yaw, halfWidth, bottom, top) {
  this.rb_1 = yaw;
  this.sb_1 = halfWidth;
  this.tb_1 = bottom;
  this.ub_1 = top;
}
protoOf(AngularRect).toString = function () {
  return 'AngularRect(yaw=' + this.rb_1 + ', halfWidth=' + this.sb_1 + ', bottom=' + this.tb_1 + ', top=' + this.ub_1 + ')';
};
protoOf(AngularRect).hashCode = function () {
  var result = getNumberHashCode(this.rb_1);
  result = imul(result, 31) + getNumberHashCode(this.sb_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.tb_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ub_1) | 0;
  return result;
};
protoOf(AngularRect).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof AngularRect))
    return false;
  if (!equals(this.rb_1, other.rb_1))
    return false;
  if (!equals(this.sb_1, other.sb_1))
    return false;
  if (!equals(this.tb_1, other.tb_1))
    return false;
  if (!equals(this.ub_1, other.ub_1))
    return false;
  return true;
};
function PanelRect(left, top, width, height) {
  this.re_1 = left;
  this.se_1 = top;
  this.te_1 = width;
  this.ue_1 = height;
}
protoOf(PanelRect).ve = function () {
  return this.re_1 + this.te_1;
};
protoOf(PanelRect).we = function () {
  return this.se_1 + this.ue_1;
};
protoOf(PanelRect).xe = function (x, y) {
  var tmp;
  var containsLower = this.re_1;
  if (x <= this.ve() ? containsLower <= x : false) {
    var containsLower_0 = this.se_1;
    tmp = y <= this.we() ? containsLower_0 <= y : false;
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(PanelRect).toString = function () {
  return 'PanelRect(left=' + this.re_1 + ', top=' + this.se_1 + ', width=' + this.te_1 + ', height=' + this.ue_1 + ')';
};
protoOf(PanelRect).hashCode = function () {
  var result = getNumberHashCode(this.re_1);
  result = imul(result, 31) + getNumberHashCode(this.se_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.te_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.ue_1) | 0;
  return result;
};
protoOf(PanelRect).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PanelRect))
    return false;
  if (!equals(this.re_1, other.re_1))
    return false;
  if (!equals(this.se_1, other.se_1))
    return false;
  if (!equals(this.te_1, other.te_1))
    return false;
  if (!equals(this.ue_1, other.ue_1))
    return false;
  return true;
};
function button($this, bar, left) {
  return new PanelRect(left, bar.se_1 + (bar.ue_1 - 40.0) / 2.0, 40.0, 40.0);
}
function TitleBar() {
  this.cc_1 = 14.0;
  this.dc_1 = 56.0;
  this.ec_1 = 40.0;
  this.fc_1 = 6.0;
  this.gc_1 = 20.0;
  this.hc_1 = 10.0;
  this.ic_1 = 38.0;
  this.jc_1 = 0.45;
  this.kc_1 = 360.0;
  this.lc_1 = 640.0;
}
protoOf(TitleBar).ye = function (panelWidthPx, panelHeightPx) {
  var tmp = panelWidthPx * 0.45;
  // Inline function 'kotlin.math.min' call
  var tmp$ret$0 = Math.min(640.0, panelWidthPx);
  var width = coerceIn(tmp, 360.0, tmp$ret$0);
  return new PanelRect((panelWidthPx - width) / 2.0, panelHeightPx + 14.0, width, 56.0);
};
protoOf(TitleBar).ze = function (panelWidthPx, panelHeightPx) {
  var bar = this.ye(panelWidthPx, panelHeightPx);
  return button(this, bar, bar.ve() - 10.0 - 40.0);
};
protoOf(TitleBar).af = function (panelWidthPx, panelHeightPx) {
  var close = this.ze(panelWidthPx, panelHeightPx);
  return button(this, this.ye(panelWidthPx, panelHeightPx), close.re_1 - 6.0 - 40.0);
};
protoOf(TitleBar).bf = function (panelWidthPx, panelHeightPx) {
  var bar = this.ye(panelWidthPx, panelHeightPx);
  return button(this, bar, bar.re_1 + 38.0);
};
protoOf(TitleBar).mc = function (panelWidthPx, panelHeightPx, x, y, back) {
  return !this.ye(panelWidthPx, panelHeightPx).xe(x, y) ? null : back && this.bf(panelWidthPx, panelHeightPx).xe(x, y) ? HitPart_BACK_getInstance() : this.ze(panelWidthPx, panelHeightPx).xe(x, y) ? HitPart_CLOSE_getInstance() : this.af(panelWidthPx, panelHeightPx).xe(x, y) ? HitPart_PIN_getInstance() : HitPart_TITLE_BAR_getInstance();
};
var TitleBar_instance;
function TitleBar_getInstance() {
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
  this.cf_1 = new Vec3(0.0, 0.0, 0.0);
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
  this.zb_1 = x;
  this.ac_1 = y;
  this.bc_1 = z;
}
protoOf(Vec3).vc = function (s) {
  return new Vec3(this.zb_1 * s, this.ac_1 * s, this.bc_1 * s);
};
protoOf(Vec3).a = function () {
  // Inline function 'kotlin.math.sqrt' call
  var x = this.zb_1 * this.zb_1 + this.ac_1 * this.ac_1 + this.bc_1 * this.bc_1;
  return Math.sqrt(x);
};
protoOf(Vec3).toString = function () {
  var tmp = listOf([this.zb_1, this.ac_1, this.bc_1]);
  return joinToString(tmp, ' ', VOID, VOID, VOID, VOID, Vec3$toString$lambda);
};
protoOf(Vec3).hashCode = function () {
  var result = getNumberHashCode(this.zb_1);
  result = imul(result, 31) + getNumberHashCode(this.ac_1) | 0;
  result = imul(result, 31) + getNumberHashCode(this.bc_1) | 0;
  return result;
};
protoOf(Vec3).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof Vec3))
    return false;
  if (!equals(this.zb_1, other.zb_1))
    return false;
  if (!equals(this.ac_1, other.ac_1))
    return false;
  if (!equals(this.bc_1, other.bc_1))
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
protoOf(OverlayState).df = function () {
  return this.id;
};
protoOf(OverlayState).ef = function () {
  return this.yawDegrees;
};
protoOf(OverlayState).ff = function () {
  return this.pitchDegrees;
};
protoOf(OverlayState).gf = function () {
  return this.opacity;
};
protoOf(OverlayState).hf = function () {
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
protoOf(PanelState).df = function () {
  return this.id;
};
protoOf(PanelState).ef = function () {
  return this.yawDegrees;
};
protoOf(PanelState).ff = function () {
  return this.pitchDegrees;
};
protoOf(PanelState).if = function () {
  return this.slotDegrees;
};
protoOf(PanelState).jf = function () {
  return this.pinned;
};
protoOf(PanelState).kf = function () {
  return this.placedByHand;
};
function panelOf($this, id, widthPx, heightPx) {
  var pose = new PanelPose(VOID, VOID, 4.0, metres($this, widthPx), metres($this, heightPx));
  return new SimulatorPanel(id, widthPx, heightPx, pose);
}
function overlayState($this, panel, opacity, shown) {
  return new OverlayState(panel.aa_1, Companion_instance_4.mf(panel.da_1.la_1), Companion_instance_4.mf(panel.da_1.ma_1), opacity, shown);
}
function panel($this, id) {
  var tmp0_elvis_lhs = $this.of_1.hd(id);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    throw IllegalArgumentException_init_$Create$('no such panel: ' + id);
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function metres($this, px) {
  return 4.0 * px / $this.nf_1;
}
function Companion_4() {
  this.lf_1 = 4.0;
}
protoOf(Companion_4).pf = function (degrees) {
  return toRadians(degrees);
};
protoOf(Companion_4).mf = function (radians) {
  return toDegrees(radians);
};
protoOf(Companion_4).qf = function (yawDegrees, pitchDegrees) {
  var yaw = this.pf(yawDegrees);
  var pitch = this.pf(pitchDegrees);
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
  this.nf_1 = panelDistancePx;
  this.of_1 = new PanelScene();
}
protoOf(SimulatorScene).rf = function () {
  return Companion_instance_4.mf(this.of_1.zc());
};
protoOf(SimulatorScene).sf = function () {
  return this.of_1.ad();
};
protoOf(SimulatorScene).tf = function () {
  var tmp0_safe_receiver = this.of_1.v7_1;
  return tmp0_safe_receiver == null ? null : tmp0_safe_receiver.aa_1;
};
protoOf(SimulatorScene).uf = function () {
  var tmp0_safe_receiver = this.of_1.a8_1;
  return tmp0_safe_receiver == null ? null : tmp0_safe_receiver.aa_1;
};
protoOf(SimulatorScene).setCollisionPolicy = function (policy) {
  var tmp = this.of_1;
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
  this.of_1.s7_1 = degrees;
};
protoOf(SimulatorScene).setCatchUpSeconds = function (seconds) {
  this.of_1.p7_1 = seconds;
};
protoOf(SimulatorScene).setFollowsHead = function (follows) {
  this.of_1.r7_1 = follows;
};
protoOf(SimulatorScene).setHeadPitchDegrees = function (degrees) {
  this.of_1.x8_1 = Companion_instance_4.pf(degrees);
};
protoOf(SimulatorScene).setDockAutoHide = function (autoHide) {
  this.of_1.l8_1 = autoHide ? DockVisibility_AUTO_HIDE_getInstance() : DockVisibility_ALWAYS_getInstance();
};
protoOf(SimulatorScene).setWindowsAutoHide = function (autoHide, delaySeconds) {
  this.of_1.q8_1 = autoHide;
  this.of_1.r8_1 = delaySeconds;
};
protoOf(SimulatorScene).vf = function () {
  return this.of_1.t8_1;
};
protoOf(SimulatorScene).wf = function (value) {
  this.of_1.m8_1 = value;
};
protoOf(SimulatorScene).xf = function () {
  return this.of_1.m8_1;
};
protoOf(SimulatorScene).xb = function () {
  return this.of_1.xb();
};
protoOf(SimulatorScene).openOverlays = function (dockId, dockWidthPx, dockHeightPx, launcherId, launcherWidthPx, launcherHeightPx) {
  this.of_1.de(panelOf(this, dockId, dockWidthPx, dockHeightPx), panelOf(this, launcherId, launcherWidthPx, launcherHeightPx));
};
protoOf(SimulatorScene).overlays = function () {
  var tmp0_safe_receiver = this.of_1.j8_1;
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = overlayState(this, tmp0_safe_receiver, this.of_1.o8_1, this.of_1.n8_1);
  }
  var tmp_0 = tmp;
  var tmp1_safe_receiver = this.of_1.k8_1;
  var tmp_1;
  if (tmp1_safe_receiver == null) {
    tmp_1 = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp_1 = overlayState(this, tmp1_safe_receiver, this.of_1.p8_1, this.of_1.xb());
  }
  // Inline function 'kotlin.collections.toTypedArray' call
  var this_0 = listOfNotNull([tmp_0, tmp_1]);
  return copyToArray(this_0);
};
protoOf(SimulatorScene).focusAndCentre = function (id, headYawDegrees) {
  var panel_0 = panel(this, id);
  this.of_1.ee(panel_0);
  this.of_1.fe(panel_0, Companion_instance_4.pf(headYawDegrees));
};
protoOf(SimulatorScene).setSlotSpacingDegrees = function (degrees) {
  if (!(degrees === this.of_1.c7_1)) {
    this.of_1.md(degrees, false);
  }
};
protoOf(SimulatorScene).open = function (id, widthPx, heightPx) {
  this.of_1.qd(panelOf(this, id, widthPx, heightPx));
};
protoOf(SimulatorScene).resize = function (id, widthPx, heightPx, draggedCorner) {
  var panel_0 = panel(this, id);
  var oldWidth = panel_0.gg_1;
  var oldHeight = panel_0.hg_1;
  panel_0.gg_1 = widthPx;
  panel_0.hg_1 = heightPx;
  this.of_1.yd(panel_0, oldWidth, oldHeight, valueOf(draggedCorner).ie());
};
protoOf(SimulatorScene).close = function (id) {
  this.of_1.cd(panel(this, id));
  this.of_1.md(VOID, false);
};
protoOf(SimulatorScene).setPinned = function (id, pinned) {
  var panel_0 = panel(this, id);
  if (panel_0.ea_1.qa_1 === pinned) {
    this.of_1.nd(panel_0, panel_0.ea_1.ne(!pinned));
  }
};
protoOf(SimulatorScene).bringIntoView = function (id, headYawDegrees) {
  return this.of_1.od(panel(this, id), Companion_instance_4.pf(headYawDegrees));
};
protoOf(SimulatorScene).recentre = function () {
  return this.of_1.id();
};
protoOf(SimulatorScene).advance = function (headYawDegrees, dt) {
  return this.of_1.wd(Companion_instance_4.pf(headYawDegrees), dt);
};
protoOf(SimulatorScene).hitTest = function (yawDegrees, pitchDegrees) {
  var tmp0_safe_receiver = this.of_1.he(Companion_instance_4.qf(yawDegrees, pitchDegrees));
  var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.u6_1;
  return tmp1_safe_receiver == null ? null : tmp1_safe_receiver.aa_1;
};
protoOf(SimulatorScene).beginGrab = function (id, aimYawDegrees, aimPitchDegrees) {
  return this.of_1.rd(panel(this, id), Companion_instance_4.qf(aimYawDegrees, aimPitchDegrees));
};
protoOf(SimulatorScene).updateGrab = function (aimYawDegrees, aimPitchDegrees) {
  return this.of_1.td(Companion_instance_4.qf(aimYawDegrees, aimPitchDegrees));
};
protoOf(SimulatorScene).endGrab = function (headYawDegrees) {
  this.of_1.ud(Companion_instance_4.pf(headYawDegrees));
};
protoOf(SimulatorScene).cancelGrab = function () {
  return this.of_1.sd();
};
protoOf(SimulatorScene).titleBar = function (widthPx, heightPx) {
  // Inline function 'kotlin.let' call
  var it = TitleBar_instance.ye(widthPx, heightPx);
  // Inline function 'kotlin.arrayOf' call
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return [it.re_1, it.se_1, it.te_1, it.ue_1];
};
protoOf(SimulatorScene).panels = function () {
  // Inline function 'kotlin.collections.map' call
  var this_0 = this.of_1.yc();
  // Inline function 'kotlin.collections.mapTo' call
  var destination = ArrayList_init_$Create$_0(collectionSizeOrDefault(this_0, 10));
  var _iterator__ex2g4s = this_0.g();
  while (_iterator__ex2g4s.h()) {
    var item = _iterator__ex2g4s.i();
    var tmp$ret$2 = new PanelState(item.aa_1, Companion_instance_4.mf(item.da_1.la_1), Companion_instance_4.mf(item.da_1.ma_1), Companion_instance_4.mf(item.fa_1), !item.ea_1.qa_1, item.ga_1);
    destination.e(tmp$ret$2);
  }
  // Inline function 'kotlin.collections.toTypedArray' call
  return copyToArray(destination);
};
protoOf(SimulatorScene).describe = function (headYawDegrees) {
  return this.of_1.xd(Companion_instance_4.pf(headYawDegrees));
};
function SimulatorPanel(id, widthPx, heightPx, pose) {
  ScenePanel.call(this, id, pose);
  this.gg_1 = widthPx;
  this.hg_1 = heightPx;
}
protoOf(SimulatorPanel).eb = function () {
  return this.gg_1;
};
protoOf(SimulatorPanel).fb = function () {
  return this.hg_1;
};
//region block: post-declaration
defineProp(protoOf(SimulatorScene), 'anchorYawDegrees', protoOf(SimulatorScene).rf);
defineProp(protoOf(SimulatorScene), 'returning', protoOf(SimulatorScene).sf);
defineProp(protoOf(SimulatorScene), 'focusedId', protoOf(SimulatorScene).tf);
defineProp(protoOf(SimulatorScene), 'grabbedId', protoOf(SimulatorScene).uf);
defineProp(protoOf(SimulatorScene), 'windowsOpacity', protoOf(SimulatorScene).vf);
defineProp(protoOf(SimulatorScene), 'launcherRequested', protoOf(SimulatorScene).xf, protoOf(SimulatorScene).wf);
defineProp(protoOf(SimulatorScene), 'launcherShown', protoOf(SimulatorScene).xb);
//endregion
//region block: init
ResizeHandles_instance = new ResizeHandles();
Companion_instance_2 = new Companion_2();
TitleBar_instance = new TitleBar();
Companion_instance_4 = new Companion_4();
//endregion
//region block: exports
export {
  OverlayState as OverlayState,
  PanelState as PanelState,
  SimulatorScene as SimulatorScene,
};
//endregion


