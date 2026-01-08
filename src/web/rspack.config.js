import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default {
    entry: {
        main: './src/web/main.js',
        charts: './src/web/charts.js'
    },
    output: {
        path: path.resolve(__dirname, '../../src/main/resources/static'),
        chunkFilename: '[id].js',
        sourceMap: false,
        library: {
            type: "window"
        }
    },
    module: {
        rules: [
            {
                test: /\.css$/,
                use: [{
                    loader: "postcss-loader"
                }],
                type: "css"
            },
        ]
    },
    experiments: {
        css: true,
    }
}