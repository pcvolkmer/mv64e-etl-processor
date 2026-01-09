import * as echarts from 'echarts/core';
import { BarChart, PieChart } from 'echarts/charts';
import { SVGRenderer } from 'echarts/renderers';
import {
    TitleComponent,
    TooltipComponent,
    DatasetComponent,
    GridComponent
} from 'echarts/components';

echarts.use([
    BarChart,
    PieChart,
    TitleComponent,
    TooltipComponent,
    DatasetComponent,
    GridComponent,
    SVGRenderer
]);

window.onload = () => {
    drawPieChart('statistics/requeststates', 'piechart1', 'Statusverteilung aller Anfragen');
    drawPieChart('statistics/requestpatientstates', 'piechart2', 'Statusverteilung nach Patient');
    drawBarChart('statistics/requestslastmonth', 'barchart', 'Anfragen der letzten 30 Tage');

    drawPieChart('statistics/requeststates?delete=true', 'piechartdel1', 'Statusverteilung aller Anfragen');
    drawPieChart('statistics/requestpatientstates?delete=true', 'piechartdel2', 'Statusverteilung nach Patient');
    drawBarChart('statistics/requestslastmonth?delete=true', 'barchartdel', 'Anfragen der letzten 30 Tage');

    const eventSource = new EventSource('statistics/events');
    eventSource.addEventListener('requeststates', event => {
        drawPieChart('statistics/requeststates', 'piechart1', 'Statusverteilung aller Anfragen', JSON.parse(event.data));
    });
    eventSource.addEventListener('requestpatientstates', event => {
        drawPieChart('statistics/requestpatientstates', 'piechart2', 'Statusverteilung nach Patient', JSON.parse(event.data));
    });
    eventSource.addEventListener('requestslastmonth', event => {
        drawBarChart('statistics/requestslastmonth', 'barchart', 'Anfragen des letzten Monats', JSON.parse(event.data));
    });

    eventSource.addEventListener('deleterequeststates', event => {
        drawPieChart('statistics/requeststates?delete=true', 'piechartdel1', 'Statusverteilung aller Anfragen', JSON.parse(event.data));
    });
    eventSource.addEventListener('deleterequestpatientstates', event => {
        drawPieChart('statistics/requestpatientstates?delete=true', 'piechartdel2', 'Statusverteilung nach Patient', JSON.parse(event.data));
    });
    eventSource.addEventListener('deleterequestslastmonth', event => {
        drawBarChart('statistics/requestslastmonth?delete=true', 'barchartdel', 'Anfragen des letzten Monats', JSON.parse(event.data));
    });
}

const dateFormatOptions = { year: 'numeric', month: '2-digit', day: '2-digit' };
const dateFormat = new Intl.DateTimeFormat('de-DE', dateFormatOptions);

export function drawPieChart(url, elemId, title, data) {
    if (data) {
        update(elemId, data);
    } else {
        fetch(url)
            .then(resp => resp.json())
            .then(data => {
                draw(elemId, title, data);
                update(elemId, data);
            });
    }

    const update = (elemId, data) => {
        let chartDom = document.getElementById(elemId);
        let chart = echarts.init(chartDom, null, {renderer: 'svg'});

        let option = {
            color: data.map(i => i.color),
            animationDuration: 250,
            animationDurationUpdate: 250,
            series: [
                {
                    type: 'pie',
                    radius: ['40%', '70%'],
                    avoidLabelOverlap: false,
                    label: {
                        show: false,
                        position: 'center'
                    },
                    labelLine: {
                        show: false
                    },
                    data: data
                }
            ]
        };

        option && chart.setOption(option);
    }

    const draw = (elemId, title, data) => {
        let chartDom = document.getElementById(elemId);
        let chart = echarts.init(chartDom, null, {renderer: 'svg'});
        let option= {
            title: {
                text: title,
                left: 'center'
            },
            tooltip: {
                trigger: 'item'
            },
            color: data.map(i => i.color),
            animationDuration: 250,
            animationDurationUpdate: 250
        };

        option && chart.setOption(option);
    }
}

export function drawBarChart(url, elemId, title, data) {
    if (data) {
        update(elemId, data);
    } else {
        fetch(url)
            .then(resp => resp.json())
            .then(data => {
                draw(elemId, title, data);
                update(elemId, data);
            });
    }

    const update = (elemId, data) => {
        let chartDom = document.getElementById(elemId);
        let chart = echarts.init(chartDom, null, {renderer: 'svg'});

        let option = {
            series: [
                {
                    name: 'ERROR',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.error)
                },
                {
                    name: 'WARNING',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.warning)
                },
                {
                    name: 'SUCCESS',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.success)
                },
                {
                    name: 'NO_CONSENT',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.no_consent)
                },
                {
                    name: 'DUPLICATION',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.duplication)
                },
                {
                    name: 'BLOCKED_INITIAL',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.blocked_initial)
                },
                {
                    name: 'UNKNOWN',
                    type: 'bar',
                    stack: 'total',
                    data: data.map(i => i.nameValues.unknown)
                }
            ]
        };

        option && chart.setOption(option);
    }

    const draw = (elemId, title, data) => {
        let chartDom = document.getElementById(elemId);
        let chart = echarts.init(chartDom, null, {renderer: 'svg'});
        let option= {
            title: {
                text: title,
                left: 'center'
            },
            xAxis: {
                type: 'category',
                data: data.map(i => dateFormat.format(Date.parse(i.date)))
            },
            yAxis: {
                type: 'value',
                minInterval: 1
            },
            tooltip: {
                trigger: 'item'
            },
            color: [
                '#FF0000',
                '#FF8C00',
                '#008000',
                '#004A9D',
                '#708090',
                '#708090',
                '#708090'
            ],
            animationDuration: 250,
            animationDurationUpdate: 250
        };

        option && chart.setOption(option);
    }
}