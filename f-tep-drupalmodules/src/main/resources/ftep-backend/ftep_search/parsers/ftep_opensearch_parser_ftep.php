<?php
use Monolog\Handler\StreamHandler;
use Monolog\Logger;

class FtepResponseHandler
{
    static protected $namespaces = array(
        '' => 'http://www.w3.org/2005/Atom',
        'time' => 'http://a9.com/-/opensearch/extensions/time/1.0/',
        'geo' => 'http://a9.com/-/opensearch/extensions/geo/1.0/',
        'os' => 'http://a9.com/-/spec/opensearch/1.1/',
        'eo' => 'http://a9.com/-/opensearch/extensions/eo/1.0/',
        'dc' => 'http://purl.org/dc/elements/1.1/',
        'georss' => 'http://www.georss.org/georss',
        'gml' => 'http://www.opengis.net/gml',
        'metalink' => 'urn:ietf:params:xml:ns:metalink',
        'xlink' => 'http://www.w3.org/1999/xlink',
        'media' => 'http://search.yahoo.com/mrss/',
    );
    protected $_log;
    protected $alias = array(
        'bbox' => 'box',
        'startDate' => null,
        'endDate' => null,
        'platform' => null,
        'name' => 'identifier',
    );

    public function __construct()
    {
        $this->_log = new Logger(basename(__FILE__));
        $this->_log->pushHandler(new StreamHandler('/var/log/ftep.log', Logger::DEBUG));
    }

    public function aliasSearchParameters($params)
    {
        foreach ($this->alias as $k => $v) {

            if (array_key_exists($k, $params)) {
                $this->_log->debug(__METHOD__ . ' - ' . __LINE__ . ' - Aliasing $k as $v');
                $old = $params[$k];
                unset($params[$k]);
                if (null !== $v) {
                    $params[$v] = $old;
                }
            }
        }
        return $params;
    }

    public function parse($data)
    {
        $response = $data->getBody()->getContents();
        if (is_string($response)) {
            $xml = simplexml_load_string($response);
        }
        $this->_log->debug(__METHOD__ . ' - ' . __LINE__ . ' - Parsing response ' . var_export($xml, true));

        $namespaces = $xml->getNamespaces(true);
        if (isset($namespaces[""])) {
            // register a prefix for the default namespace:
            $xml->registerXPathNamespace('default', $namespaces[""]);
        }

        foreach (FtepResponseHandler::$namespaces as $n => $ns) {
            $xml->registerXPathNamespace($n, $ns);
        }

        $result = array(
            'totalResults' => (string)$xml->xpath('//os:totalResults')[0],
            'startIndex' => (string)$xml->xpath('//os:startIndex')[0],
            'itemsPerPage' => (string)$xml->xpath('//os:itemsPerPage')[0],
        );
        foreach ($xml->xpath('//default:entry') as $entry) {
            $entry = $this->setupNamespaces($entry);
            $ent['title'] = (string)$entry->title;
            $ent['identifier'] = (string)$entry->title;
            $ent['start'] = '';//2014-12-02T12:25:32.394945";
            $ent['stop'] = '';//2014-12-02T12:25:32.394945";
            $ent['link'] = $this->getTagAttribute('default:link[@rel="enclosure"]', $entry, 'href');
            $ent['size'] = $this->getTagAttribute('default:link[@rel="enclosure"]', $entry, 'length');
            $ent['type'] = $this->getTagAttribute('default:link[@rel="enclosure"]', $entry, 'type');
            $id = (string)$entry->id;
            $meta = $this->getTagAttribute('default:link[@type="application/json"]', $entry, 'href');
            $ent['meta'] = $meta;
            $urls[$id] = array('url' => $meta, 'payload' => $ent);

            if (array_key_exists('georss', $namespaces)) {
                $georss = $entry->children($namespaces['georss']);
                $georss->addAttribute('xmlns:xmlns:georss', FtepResponseHandler::$namespaces['georss']);
                $geom = geoPHP::load($georss->asXML(), 'georss');
                $ent['geo'] = json_decode($geom->out('json'));
            }
            $result['entities'][] = $ent;
        }
        return $result;
    }

    private function setupNamespaces(SimpleXMLElement $x)
    {
        $namespaces = $x->getNamespaces(true);
        if (isset($namespaces[""])) {
            // register a prefix for the default namespace:
            $x->registerXPathNamespace("default", $namespaces[""]);
        }

        foreach (FtepResponseHandler::$namespaces as $n => $ns) {
            $x->registerXPathNamespace($n, $ns);
        }
        return $x;
    }

    private function getTagAttribute($xpath, SimpleXMLElement $xml, $attribute)
    {
        $result = null;
        $link_tag = $xml->xpath($xpath);
        if ($link_tag && is_array($link_tag)) {
            $result = (string)$link_tag[0]->attributes()->{$attribute};
        }
        return $result;
    }
}
