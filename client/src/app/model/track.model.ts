import {TrackState} from './track-state.enum';
import {TrackSource} from './track-source';

export interface Track {
    id: string;
    title: string;
    singer: string;
    duration: number;
    state: TrackState;
    uri: string;
    source: TrackSource;
    playPosition: number;
    isRandomlyChosen: boolean;
}
