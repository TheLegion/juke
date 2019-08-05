import {Track} from './track.model';

export interface PlayerState {
    playlist: Track[];
    currentTrack: Track;
    volume: number;
    playDuration: number;
}
