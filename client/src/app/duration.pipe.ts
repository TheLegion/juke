import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
    name: 'duration'
})
export class DurationPipe implements PipeTransform {

    transform(value: number, ...args: any[]): any {
        const seconds = String(value % 60).padStart(2, '0');
        const minutes = Math.floor(value / 60);
        return `${minutes}:${seconds}`;
    }

}
