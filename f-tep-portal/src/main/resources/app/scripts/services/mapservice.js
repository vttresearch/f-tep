/**
 * @ngdoc service
 * @name ftepApp.MapService
 * @description
 * # MapService
 * Service in the ftepApp.
 */
define(['../ftepmodules', 'ol'], function (ftepmodules, ol) {
    'use strict';

    ftepmodules.service('MapService', ['$http', '$rootScope', 'MessageService', 'ftepProperties', function ($http, $rootScope, MessageService, ftepProperties) {

        this.mapstore = {
            aoi: { selectedArea: undefined, wkt: undefined },
            type: {active: 'Satellite Street'},
            location: [0, 51.28],
            zoom: 4
        };
        this.searchLayerFeatures = new ol.Collection();
        this.resultLayerFeatures = new ol.Collection();
        this.basketLayerFeatures = new ol.Collection();

        this.getPolygonWkt = function(){
            return angular.copy(this.mapstore.aoi.wkt);
        };

        this.resetSearchPolygon = function(){
            this.mapstore.aoi.selectedArea = undefined;
            this.mapstore.aoi.wkt = undefined;
        };

        var rootUri = ftepProperties.FTEP_URL;

        this.updateDefaultSlds = function(parent) {
            return $http({
                method: 'GET',
                url: rootUri + '/app/slds.json'
            }).
            then(function (response) {
                //console.log(response.data);
                let slds = {}
                for (var i=0; i<response.data.slds.length; i++) {
                  slds[response.data.slds[i].id] = response.data.slds[i];
                }
                //console.log(slds);
                parent.defaultSlds = slds;
                $rootScope.$broadcast('slds.updated', response.data.slds);
            })
            .catch(function(error) {
                MessageService.addError('Unable to get default GeoServer styles', error);
            });
        };

        this.updateDefaultSlds(this);

        return this;
    }]);
});
