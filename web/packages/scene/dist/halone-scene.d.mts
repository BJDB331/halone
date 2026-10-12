type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare class OverlayState {
    constructor(id: string, yawDegrees: number, pitchDegrees: number, opacity: number, shown: boolean);
    get id(): string;
    get yawDegrees(): number;
    get pitchDegrees(): number;
    get opacity(): number;
    get shown(): boolean;
}
export declare namespace OverlayState {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => OverlayState;
    }
}
export declare class PanelState {
    constructor(id: string, yawDegrees: number, pitchDegrees: number, slotDegrees: number, pinned: boolean, placedByHand: boolean);
    get id(): string;
    get yawDegrees(): number;
    get pitchDegrees(): number;
    get slotDegrees(): number;
    get pinned(): boolean;
    get placedByHand(): boolean;
}
export declare namespace PanelState {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => PanelState;
    }
}
export declare class SimulatorScene {
    constructor(panelDistancePx: number);
    get anchorYawDegrees(): number;
    get returning(): boolean;
    get focusedId(): Nullable<string>;
    get grabbedId(): Nullable<string>;
    setCollisionPolicy(policy: string): void;
    setFollowMarginDegrees(degrees: number): void;
    setCatchUpSeconds(seconds: number): void;
    setFollowsHead(follows: boolean): void;
    setHeadPitchDegrees(degrees: number): void;
    setDockAutoHide(autoHide: boolean): void;
    setWindowsAutoHide(autoHide: boolean, delaySeconds: number): void;
    get windowsOpacity(): number;
    get launcherRequested(): boolean;
    set launcherRequested(value: boolean);
    get launcherShown(): boolean;
    openOverlays(dockId: string, dockWidthPx: number, dockHeightPx: number, launcherId: string, launcherWidthPx: number, launcherHeightPx: number): void;
    overlays(): Array<OverlayState>;
    focusAndCentre(id: string, headYawDegrees: number): void;
    setSlotSpacingDegrees(degrees: number): void;
    open(id: string, widthPx: number, heightPx: number): void;
    resize(id: string, widthPx: number, heightPx: number, draggedCorner: string): void;
    close(id: string): void;
    setPinned(id: string, pinned: boolean): void;
    bringIntoView(id: string, headYawDegrees: number): boolean;
    recentre(): void;
    advance(headYawDegrees: number, dt: number): boolean;
    hitTest(yawDegrees: number, pitchDegrees: number): Nullable<string>;
    beginGrab(id: string, aimYawDegrees: number, aimPitchDegrees: number): boolean;
    updateGrab(aimYawDegrees: number, aimPitchDegrees: number): void;
    endGrab(headYawDegrees: number): void;
    cancelGrab(): void;
    titleBar(widthPx: number, heightPx: number): Array<number>;
    panels(): Array<PanelState>;
    describe(headYawDegrees: number): string;
}
export declare namespace SimulatorScene {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SimulatorScene;
    }
}